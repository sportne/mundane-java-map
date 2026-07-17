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
TOOL_LICENSE_ID='MIT'
TOOL_LICENSE_PATH='licenses/GDAL-MIT.txt'
DATA_LICENSE_ID='BSD-3-Clause'
DATA_LICENSE_PATH='licenses/BSD-3-Clause.txt'

if [[ $# -ne 1 ]]; then
    echo 'usage: gdal-3.13.0-zone-v.sh EMPTY_OUTPUT_DIRECTORY' >&2
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
    echo 'GDAL image identity does not match the approved Linux/amd64 manifest' >&2
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
    --env GDAL_DTED_SINGLE_BLOCK=YES \
    --env DTED_VERIFY_CHECKSUM=YES \
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
mkdir scratch candidates

python3 - <<'PYTHON_SCRIPT'
from array import array
from hashlib import sha256
from pathlib import Path
import sys

from osgeo import gdal

gdal.UseExceptions()
gdal.SetConfigOption("GDAL_PAM_ENABLED", "NO")
gdal.SetConfigOption("DTED_VERIFY_CHECKSUM", "YES")

ROOT = Path("/work")
SCRATCH = ROOT / "scratch"
CANDIDATES = ROOT / "candidates"
NODATA = -32767
DATASETS = (
    ("gdal-zone-v-l0-complete", 0, 21, 121, 8762),
    ("gdal-zone-v-l1-complete", 1, 201, 1201, 488642),
    ("gdal-zone-v-l2-partial", 2, 601, 3601, 4339042),
)


def value(column, row, columns, rows):
    return 1500 + (1000 * column) // (columns - 1) - (2000 * row) // (rows - 1)


def is_void(level, column, row):
    return level == 2 and 299 <= column <= 301 and 1799 <= row <= 1801


def write_raw(path, level, columns, rows):
    with path.open("wb") as output:
        for row in range(rows):
            values = array(
                "h",
                (
                    NODATA if is_void(level, column, row) else value(column, row, columns, rows)
                    for column in range(columns)
                ),
            )
            if sys.byteorder != "little":
                values.byteswap()
            values.tofile(output)


def write_vrt(path, raw_name, columns, rows):
    longitude_spacing = 1.0 / (columns - 1)
    latitude_spacing = 1.0 / (rows - 1)
    x_origin = -1.0 - longitude_spacing / 2.0
    y_origin = -80.0 + latitude_spacing / 2.0
    path.write_text(
        f'''<VRTDataset rasterXSize="{columns}" rasterYSize="{rows}">
  <SRS dataAxisToSRSAxisMapping="2,1">EPSG:4326</SRS>
  <GeoTransform>{x_origin:.17g}, {longitude_spacing:.17g}, 0, {y_origin:.17g}, 0, {-latitude_spacing:.17g}</GeoTransform>
  <Metadata>
    <MDI key="DTED_CompilationDate">2605</MDI>
    <MDI key="DTED_HorizontalDatum">WGS84</MDI>
    <MDI key="DTED_VerticalDatum">MSL</MDI>
  </Metadata>
  <VRTRasterBand dataType="Int16" band="1" subClass="VRTRawRasterBand">
    <NoDataValue>{NODATA}</NoDataValue>
    <SourceFilename relativeToVRT="1">{raw_name}</SourceFilename>
    <ImageOffset>0</ImageOffset>
    <PixelOffset>2</PixelOffset>
    <LineOffset>{columns * 2}</LineOffset>
    <ByteOrder>LSB</ByteOrder>
  </VRTRasterBand>
</VRTDataset>
''',
        encoding="utf-8",
        newline="\n",
    )


def require_equal(actual, expected, description):
    if actual != expected:
        raise RuntimeError(f"{description}: expected {expected!r}, got {actual!r}")


for dataset_id, level, columns, rows, expected_bytes in DATASETS:
    raw = SCRATCH / f"level-{level}.raw"
    vrt = SCRATCH / f"level-{level}.vrt"
    target = CANDIDATES / dataset_id / "w001" / f"s81.dt{level}"
    target.parent.mkdir(parents=True)
    write_raw(raw, level, columns, rows)
    write_vrt(vrt, raw.name, columns, rows)

    messages = []

    def error_handler(error_class, error_number, message):
        messages.append((error_class, error_number, message))

    gdal.PushErrorHandler(error_handler)
    try:
        source = gdal.Open(str(vrt), gdal.GA_ReadOnly)
        output = gdal.GetDriverByName("DTED").CreateCopy(str(target), source, strict=1)
        if output is None:
            raise RuntimeError("GDAL DTED CreateCopy returned no dataset")
        output.FlushCache()
        output = None
        source = None
    finally:
        gdal.PopErrorHandler()
    if messages:
        raise RuntimeError(f"GDAL emitted warning/error messages: {messages!r}")

    require_equal(target.stat().st_size, expected_bytes, "DTED byte length")
    reopened = gdal.Open(str(target), gdal.GA_ReadOnly)
    require_equal(reopened.RasterXSize, columns, "column count")
    require_equal(reopened.RasterYSize, rows, "row count")
    require_equal(reopened.GetMetadataItem("DTED_CompilationDate"), "2605", "compilation date")
    require_equal(
        reopened.GetMetadataItem("DTED_PartialCellIndicator"),
        "99" if level == 2 else "00",
        "partial-cell indicator",
    )
    band = reopened.GetRasterBand(1)
    band.Checksum()
    require_equal(int(band.ReadAsArray(0, 0, 1, 1)[0, 0]), 1500, "north-west sample")
    require_equal(
        int(band.ReadAsArray(columns - 1, rows - 1, 1, 1)[0, 0]),
        500,
        "south-east sample",
    )
    if level == 2:
        require_equal(
            int(band.ReadAsArray(300, 1800, 1, 1)[0, 0]), NODATA, "Level 2 void"
        )
    reopened = None
    digest = sha256(target.read_bytes()).hexdigest()
    print(f"{dataset_id}\t{expected_bytes}\t{digest}")
    raw.unlink()
    vrt.unlink()

SCRATCH.rmdir()
PYTHON_SCRIPT

find candidates -type f -print0 | sort -z | xargs -0 sha256sum
CONTAINER_SCRIPT
