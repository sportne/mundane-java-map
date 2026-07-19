#!/usr/bin/env bash
set -euo pipefail

export LC_ALL=C
export LANG=C
export TZ=UTC
export PYTHONHASHSEED=0
umask 022

IMAGE_TAG='ghcr.io/osgeo/gdal:ubuntu-full-3.13.0'
IMAGE_MANIFEST_DIGEST='sha256:fd205102ddfaa537e18dac37a9f648e79989e99a4e6f6a2375e5f7e0e511616c'
IMAGE_CONFIG_DIGEST='sha256:be85b2a4b798f1d2f10bb9b724336976ce1bf1b0791298b8ad8379fc012d3138'
IMAGE_PLATFORM='linux/amd64'
GDAL_VERSION='GDAL 3.13.0 "Iowa City", released 2026/05/04'

if [[ $# -ne 1 ]]; then
    echo 'usage: gdal-3.13.0-geotiff.sh EMPTY_OUTPUT_DIRECTORY' >&2
    exit 2
fi

output_directory=$1
mkdir -p "$output_directory"
if [[ -n "$(find "$output_directory" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    echo 'output directory must be empty' >&2
    exit 2
fi
output_directory=$(cd "$output_directory" && pwd -P)

image_reference="${IMAGE_TAG}@${IMAGE_MANIFEST_DIGEST}"
run_reference=${MUNDANE_GDAL_IMAGE_OVERRIDE:-$image_reference}
if ! docker image inspect "$run_reference" >/dev/null 2>&1; then
    if [[ -n "${MUNDANE_GDAL_IMAGE_OVERRIDE:-}" ]]; then
        echo 'the explicitly supplied local GDAL image is unavailable' >&2
        exit 2
    fi
    docker pull --platform "$IMAGE_PLATFORM" "$image_reference" >/dev/null
fi

actual_config=$(docker image inspect "$run_reference" --format '{{.Id}}')
actual_platform=$(docker image inspect "$run_reference" --format '{{.Os}}/{{.Architecture}}')
if [[ "$actual_config" != "$IMAGE_CONFIG_DIGEST" || "$actual_platform" != "$IMAGE_PLATFORM" ]]; then
    echo 'GDAL image identity does not match the pinned Linux/amd64 manifest' >&2
    exit 2
fi
actual_version=$(docker run --rm --network none --platform "$IMAGE_PLATFORM" "$run_reference" gdal --version)
if [[ "$actual_version" != "$GDAL_VERSION" ]]; then
    echo "unexpected GDAL version: $actual_version" >&2
    exit 2
fi

docker run --rm --interactive \
    --network none \
    --platform "$IMAGE_PLATFORM" \
    --env GDAL_PAM_ENABLED=NO \
    --env LC_ALL=C \
    --env LANG=C \
    --env TZ=UTC \
    --env PYTHONHASHSEED=0 \
    --volume "$output_directory:/work" \
    --workdir /work \
    "$run_reference" \
    bash -s <<'CONTAINER_SCRIPT'
set -euo pipefail
umask 022

python3 - <<'PYTHON_SCRIPT'
from array import array
from pathlib import Path
import platform
import sys

from osgeo import gdal, osr

gdal.UseExceptions()
gdal.SetConfigOption("GDAL_PAM_ENABLED", "NO")
ROOT = Path("/work")
if platform.python_version() != "3.14.4":
    raise RuntimeError(f"unexpected CPython version: {platform.python_version()}")
proj_version = (
    osr.GetPROJVersionMajor(), osr.GetPROJVersionMinor(), osr.GetPROJVersionMicro()
)
if proj_version != (9, 8, 1):
    raise RuntimeError(f"unexpected internal PROJ version: {proj_version!r}")


def projection(epsg):
    reference = osr.SpatialReference()
    reference.ImportFromEPSG(epsg)
    reference.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER)
    return reference.ExportToWkt()


def write_band(band, kind, values):
    payload = array(kind, values)
    if sys.byteorder != "little" and kind != "B":
        payload.byteswap()
    band.WriteRaster(0, 0, band.XSize, band.YSize, payload.tobytes())


def create(name, width, height, bands, data_type, epsg, transform, options, point, formula):
    messages = []

    def error_handler(error_class, error_number, message):
        messages.append((error_class, error_number, message))

    gdal.PushErrorHandler(error_handler)
    try:
        dataset = gdal.GetDriverByName("GTiff").Create(
            str(ROOT / name), width, height, bands, data_type, options=options
        )
        if dataset is None:
            raise RuntimeError("GDAL GTiff Create returned no dataset")
        dataset.SetProjection(projection(epsg))
        dataset.SetGeoTransform(transform)
        dataset.SetMetadataItem("AREA_OR_POINT", "Point" if point else "Area")
        formula(dataset)
        dataset.FlushCache()
        dataset = None
    finally:
        gdal.PopErrorHandler()
    if messages:
        raise RuntimeError(f"GDAL emitted warning/error messages: {messages!r}")


create(
    "gdal-rgb-strip-none-4326.tif",
    24,
    16,
    3,
    gdal.GDT_Byte,
    4326,
    (-10.0, 0.1, 0.0, 20.0, 0.0, -0.1),
    ["COMPRESS=NONE", "INTERLEAVE=PIXEL", "TILED=NO", "BLOCKYSIZE=16", "GEOTIFF_VERSION=1.1"],
    False,
    lambda dataset: [
        write_band(
            dataset.GetRasterBand(band + 1),
            "B",
            (
                ((17, 29, 11)[band] * (column if band != 1 else row)
                 + (11 * row if band == 2 else 0)) & 255
                for row in range(16)
                for column in range(24)
            ),
        )
        for band in range(3)
    ],
)

create(
    "gdal-gray-tile-deflate-3857.tif",
    16,
    16,
    1,
    gdal.GDT_Byte,
    3857,
    (100000.0, 1000.0, 0.0, 200000.0, 0.0, -1000.0),
    ["COMPRESS=DEFLATE", "PREDICTOR=1", "TILED=YES", "BLOCKXSIZE=16", "BLOCKYSIZE=16", "GEOTIFF_VERSION=1.1"],
    False,
    lambda dataset: write_band(
        dataset.GetRasterBand(1),
        "B",
        ((13 * column + 7 * row) & 255 for row in range(16) for column in range(16)),
    ),
)

create(
    "gdal-int16-strip-packbits-4326.tif",
    16,
    16,
    1,
    gdal.GDT_Int16,
    4326,
    (-1.005, 0.01, 0.0, 1.005, 0.0, -0.01),
    ["COMPRESS=PACKBITS", "TILED=NO", "BLOCKYSIZE=16", "GEOTIFF_VERSION=1.1"],
    True,
    lambda dataset: write_band(
        dataset.GetRasterBand(1),
        "h",
        (-24000 + 500 * column + 250 * row for row in range(16) for column in range(16)),
    ),
)

create(
    "gdal-float32-tile-deflate-3857.tif",
    16,
    16,
    1,
    gdal.GDT_Float32,
    3857,
    (950.0, 100.0, 0.0, 2050.0, 0.0, -100.0),
    ["COMPRESS=DEFLATE", "PREDICTOR=1", "TILED=YES", "BLOCKXSIZE=16", "BLOCKYSIZE=16", "GEOTIFF_VERSION=1.1"],
    True,
    lambda dataset: write_band(
        dataset.GetRasterBand(1),
        "f",
        (-120.0 + 2.5 * column + 1.25 * row for row in range(16) for column in range(16)),
    ),
)
PYTHON_SCRIPT
CONTAINER_SCRIPT
