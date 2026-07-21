package io.github.mundanej.map.example.livetrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class PackedIouKalmanFilterTest {
    @Test
    void validatesConfigurationStorageAndMeasurements() {
        assertThrows(IllegalArgumentException.class, () -> new IouKalmanConfig(0.0, 20.0, 1.0));
        assertThrows(
                IllegalArgumentException.class, () -> new IouKalmanConfig(Double.NaN, 20.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new IouKalmanConfig(0.1, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new IouKalmanConfig(0.1, 1.0, 0.0));
        assertThrows(
                IllegalArgumentException.class, () -> new IouKalmanConfig(0.1, 1.0, 1_000_001.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PackedIouKalmanFilter(0, IouKalmanConfig.REFERENCE));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PackedIouKalmanFilter(1_000_001, IouKalmanConfig.REFERENCE));

        PackedIouKalmanFilter filter = new PackedIouKalmanFilter(1, IouKalmanConfig.REFERENCE);
        assertThrows(
                IllegalArgumentException.class,
                () -> filter.initialize(0, 0L, Double.POSITIVE_INFINITY, 0.0));
        assertThrows(IndexOutOfBoundsException.class, () -> filter.initialize(1, 0L, 0.0, 0.0));
        assertThrows(IllegalStateException.class, () -> filter.x(0));

        PackedIouKalmanFilter overflow = new PackedIouKalmanFilter(1, IouKalmanConfig.REFERENCE);
        overflow.initialize(0, Long.MAX_VALUE, 0.0, 0.0);
        assertFalse(overflow.update(0, Long.MIN_VALUE, 1.0, 1.0));
        assertEquals(Long.MAX_VALUE, overflow.timestampSeconds(0));
        PackedIouKalmanFilter oppositeOverflow =
                new PackedIouKalmanFilter(1, IouKalmanConfig.REFERENCE);
        oppositeOverflow.initialize(0, Long.MIN_VALUE, 0.0, 0.0);
        assertFalse(oppositeOverflow.update(0, Long.MAX_VALUE, 1.0, 1.0));
        assertEquals(Long.MIN_VALUE, oppositeOverflow.timestampSeconds(0));

        PackedIouKalmanFilter finiteState = new PackedIouKalmanFilter(1, IouKalmanConfig.REFERENCE);
        finiteState.initialize(0, 0L, Double.MAX_VALUE, 0.0);
        assertThrows(
                IllegalStateException.class,
                () -> finiteState.update(0, 1L, -Double.MAX_VALUE, 0.0));
        assertEquals(Double.MAX_VALUE, finiteState.x(0));
        assertEquals(0L, finiteState.timestampSeconds(0));
        assertEquals(1L, finiteState.acceptedReports());
        assertEquals(1L, finiteState.rejectedReports());
    }

    @Test
    void coefficientLimitsAreStableAndBounded() {
        IouKalmanConfig nearLimit = new IouKalmanConfig(1.0e-6, 20.0, 1.0);
        double[] coefficients = new double[5];
        IouModel.coefficients(nearLimit, 1.0, coefficients);
        assertEquals(0.9999995, coefficients[IouModel.A], 1.0e-12);
        assertEquals(1.0, coefficients[IouModel.E], 1.1e-6);
        assertEquals(400.0 / 3.0, coefficients[IouModel.Q00], 1.0e-3);
        assertEquals(200.0, coefficients[IouModel.Q01], 1.0e-3);
        assertEquals(400.0, coefficients[IouModel.Q11], 1.0e-3);

        IouKalmanConfig threshold = new IouKalmanConfig(0.001, 20.0, 1.0);
        for (double delta : new double[] {0.999, 1.0, 1.001}) {
            IouModel.coefficients(threshold, delta, coefficients);
            double[] oracle = highPrecisionCoefficients(threshold, delta);
            for (int index = 0; index < coefficients.length; index++) {
                assertEquals(oracle[index], coefficients[index], Math.abs(oracle[index]) * 2.0e-9);
            }
        }

        IouModel.coefficients(IouKalmanConfig.REFERENCE, 60.0, coefficients);
        for (double coefficient : coefficients) {
            assertTrue(Double.isFinite(coefficient));
            assertTrue(coefficient >= 0.0);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> IouModel.coefficients(IouKalmanConfig.REFERENCE, 0.0, coefficients));
        assertThrows(
                IllegalArgumentException.class,
                () -> IouModel.coefficients(IouKalmanConfig.REFERENCE, 61.0, coefficients));
        assertThrows(
                IllegalArgumentException.class,
                () -> IouModel.coefficients(IouKalmanConfig.REFERENCE, 1.0, new double[4]));
    }

    @Test
    void initializesUpdatesRejectsTimeAndKeepsDisplayPredictionPure() {
        PackedIouKalmanFilter filter = new PackedIouKalmanFilter(2, IouKalmanConfig.REFERENCE);
        assertEquals(2, filter.trackCount());
        assertTrue(filter.initialize(0, 100L, 10.0, 20.0));
        assertEquals(25_000_000.0, filter.positionVariance(0));
        assertEquals(0.0, filter.positionVelocityCovariance(0));
        assertEquals(4_000.0, filter.velocityVariance(0));
        assertFalse(filter.initialize(0, 100L, 11.0, 21.0));
        assertFalse(filter.update(0, 100L, 11.0, 21.0));
        assertFalse(filter.update(0, 161L, 11.0, 21.0));
        assertTrue(filter.update(0, 160L, 500.0, -300.0));

        double x = filter.x(0);
        double y = filter.y(0);
        double vx = filter.velocityX(0);
        double vy = filter.velocityY(0);
        double predictedX = filter.displayX(0, 160.5);
        double predictedY = filter.displayY(0, 160.5);
        assertTrue(Double.isFinite(predictedX));
        assertTrue(Double.isFinite(predictedY));
        assertEquals(x, filter.x(0));
        assertEquals(y, filter.y(0));
        assertEquals(vx, filter.velocityX(0));
        assertEquals(vy, filter.velocityY(0));
        assertEquals(160L, filter.timestampSeconds(0));
        assertEquals(2L, filter.acceptedReports());
        assertEquals(3L, filter.rejectedReports());
        assertThrows(IllegalArgumentException.class, () -> filter.displayX(0, 159.0));
        assertThrows(IllegalArgumentException.class, () -> filter.displayY(0, 221.0));
    }

    @Test
    void packedTracksRemainIsolated() {
        PackedIouKalmanFilter filter = new PackedIouKalmanFilter(3, IouKalmanConfig.REFERENCE);
        filter.initialize(0, 0L, 100.0, 200.0);
        filter.initialize(1, 0L, -100.0, -200.0);
        filter.initialize(2, 0L, 7.0, 9.0);
        filter.update(1, 7L, -70.0, -240.0);
        assertEquals(100.0, filter.x(0));
        assertEquals(200.0, filter.y(0));
        assertEquals(7.0, filter.x(2));
        assertEquals(9.0, filter.y(2));
        assertTrue(filter.x(1) > -100.0);
        assertTrue(filter.y(1) < -200.0);
    }

    @Test
    void packedDisplayCopyMatchesScalarPredictionForIntegerAndFractionalAges() {
        PackedIouKalmanFilter filter = new PackedIouKalmanFilter(2, IouKalmanConfig.REFERENCE);
        filter.initialize(0, 0L, 100.0, 200.0);
        filter.initialize(1, 0L, -100.0, -200.0);
        filter.update(0, 10L, 300.0, 400.0);
        filter.update(1, 20L, -300.0, -400.0);
        double[] positionsX = new double[4];
        double[] positionsY = new double[4];
        filter.copyDisplayPositions(20.5, positionsX, positionsY, 1);
        assertEquals(filter.displayX(0, 20.5), positionsX[1]);
        assertEquals(filter.displayY(0, 20.5), positionsY[1]);
        assertEquals(filter.displayX(1, 20.5), positionsX[2]);
        assertEquals(filter.displayY(1, 20.5), positionsY[2]);

        filter.copyDisplayPositions(21.0, positionsX, positionsY, 1);
        assertEquals(filter.displayX(0, 21.0), positionsX[1]);
        assertEquals(filter.displayY(0, 21.0), positionsY[1]);
        assertEquals(filter.displayX(1, 21.0), positionsX[2]);
        assertEquals(filter.displayY(1, 21.0), positionsY[2]);
    }

    @Test
    void scalarKernelAgreesWithIndependentDenseOracle() {
        assertKernelMatchesOracle(IouKalmanConfig.REFERENCE);
        assertKernelMatchesOracle(new IouKalmanConfig(1.0e-6, 20.0, 5_000.0));
    }

    private static void assertKernelMatchesOracle(IouKalmanConfig config) {
        PackedIouKalmanFilter filter = new PackedIouKalmanFilter(1, config);
        DenseOracle oracle = new DenseOracle(config, 900.0, -700.0);
        filter.initialize(0, 0L, 900.0, -700.0);
        long time = 0L;
        int[] intervals = {1, 2, 7, 60, 3, 19, 1, 60, 11};
        for (int index = 0; index < intervals.length; index++) {
            time += intervals[index];
            double measuredX = 1_000.0 + 80.0 * index + 125.0 * StrictMath.sin(index * 0.7);
            double measuredY = -800.0 + 35.0 * index - 90.0 * StrictMath.cos(index * 0.4);
            assertTrue(filter.update(0, time, measuredX, measuredY));
            oracle.update(intervals[index], measuredX, measuredY);
            assertEquals(oracle.state[0], filter.x(0), 1.0e-8);
            assertEquals(oracle.state[1], filter.velocityX(0), 1.0e-8);
            assertEquals(oracle.state[2], filter.y(0), 1.0e-8);
            assertEquals(oracle.state[3], filter.velocityY(0), 1.0e-8);
            assertEquals(oracle.covariance[0][0], filter.positionVariance(0), 1.0e-7);
            assertEquals(oracle.covariance[0][1], filter.positionVelocityCovariance(0), 1.0e-7);
            assertEquals(oracle.covariance[1][1], filter.velocityVariance(0), 1.0e-7);
            assertPositiveSemidefinite(filter);
        }
    }

    @Test
    void fixedSeedGaussianRunHasSaneInnovationAndImprovesStationaryRmse() {
        IouKalmanConfig config = new IouKalmanConfig(0.05, 5.0, 500.0);
        PackedIouKalmanFilter filter = new PackedIouKalmanFilter(1, config);
        double firstX = fixedGaussian(0, 0) * config.measurementStandardDeviation();
        double firstY = fixedGaussian(0, 1) * config.measurementStandardDeviation();
        filter.initialize(0, 0L, firstX, firstY);
        double measurementSquaredError = firstX * firstX + firstY * firstY;
        double estimateSquaredError = measurementSquaredError;
        double normalizedInnovationSquared = 0.0;
        for (int second = 1; second <= 2_000; second++) {
            double measuredX = fixedGaussian(second, 0) * config.measurementStandardDeviation();
            double measuredY = fixedGaussian(second, 1) * config.measurementStandardDeviation();
            double innovationX = measuredX - filter.displayX(0, second);
            double innovationY = measuredY - filter.displayY(0, second);
            double innovationVariance = filter.innovationVariance(0, second);
            normalizedInnovationSquared +=
                    (innovationX * innovationX + innovationY * innovationY) / innovationVariance;
            filter.update(0, second, measuredX, measuredY);
            measurementSquaredError += measuredX * measuredX + measuredY * measuredY;
            estimateSquaredError += filter.x(0) * filter.x(0) + filter.y(0) * filter.y(0);
        }
        double measurementRmse = StrictMath.sqrt(measurementSquaredError / 4_002.0);
        double estimateRmse = StrictMath.sqrt(estimateSquaredError / 4_002.0);
        assertTrue(Double.isFinite(estimateRmse));
        assertTrue(estimateRmse < measurementRmse * 0.75);
        double meanNormalizedInnovationSquared = normalizedInnovationSquared / 2_000.0;
        assertTrue(meanNormalizedInnovationSquared > 1.5);
        assertTrue(meanNormalizedInnovationSquared < 2.5);
        assertEquals(2_001L, filter.acceptedReports());
    }

    private static double fixedGaussian(int sample, int axis) {
        long first = mix64(0x4d554e44414e454cL ^ Integer.toUnsignedLong(sample));
        long second = mix64(first ^ 0x9e3779b97f4a7c15L);
        double u1 = ((first >>> 11) + 1L) * 0x1.0p-53;
        double u2 = (second >>> 11) * 0x1.0p-53;
        double radius = StrictMath.sqrt(-2.0 * StrictMath.log(u1));
        double angle = 2.0 * StrictMath.PI * u2;
        return axis == 0 ? radius * StrictMath.cos(angle) : radius * StrictMath.sin(angle);
    }

    private static long mix64(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    private static void assertPositiveSemidefinite(PackedIouKalmanFilter filter) {
        double p00 = filter.positionVariance(0);
        double p01 = filter.positionVelocityCovariance(0);
        double p11 = filter.velocityVariance(0);
        assertTrue(Double.isFinite(p00));
        assertTrue(Double.isFinite(p01));
        assertTrue(Double.isFinite(p11));
        assertTrue(p00 >= 0.0);
        assertTrue(p11 >= 0.0);
        assertTrue(p00 * p11 - p01 * p01 >= -1.0e-5);
    }

    private static final class DenseOracle {
        private final IouKalmanConfig config;
        private final double[] state = new double[4];
        private double[][] covariance = new double[4][4];

        DenseOracle(IouKalmanConfig config, double measuredX, double measuredY) {
            this.config = config;
            state[0] = measuredX;
            state[2] = measuredY;
            covariance[0][0] = config.measurementVariance();
            covariance[2][2] = config.measurementVariance();
            covariance[1][1] = config.sigma() * config.sigma() / (2.0 * config.beta());
            covariance[3][3] = covariance[1][1];
        }

        void update(double delta, double measuredX, double measuredY) {
            double[] coefficients = highPrecisionCoefficients(config, delta);
            double a = coefficients[IouModel.A];
            double e = coefficients[IouModel.E];
            double q00 = coefficients[IouModel.Q00];
            double q01 = coefficients[IouModel.Q01];
            double q11 = coefficients[IouModel.Q11];
            double[][] transition = {
                {1.0, a, 0.0, 0.0},
                {0.0, e, 0.0, 0.0},
                {0.0, 0.0, 1.0, a},
                {0.0, 0.0, 0.0, e}
            };
            double[][] processNoise = {
                {q00, q01, 0.0, 0.0},
                {q01, q11, 0.0, 0.0},
                {0.0, 0.0, q00, q01},
                {0.0, 0.0, q01, q11}
            };
            double[] predictedState = multiply(transition, state);
            covariance =
                    add(
                            multiply(multiply(transition, covariance), transpose(transition)),
                            processNoise);
            System.arraycopy(predictedState, 0, state, 0, state.length);
            measure(0, measuredX);
            measure(2, measuredY);
        }

        private void measure(int positionIndex, double measurement) {
            double innovationVariance =
                    covariance[positionIndex][positionIndex] + config.measurementVariance();
            double[] gain = new double[4];
            for (int row = 0; row < 4; row++) {
                gain[row] = covariance[row][positionIndex] / innovationVariance;
            }
            double innovation = measurement - state[positionIndex];
            for (int row = 0; row < 4; row++) {
                state[row] += gain[row] * innovation;
            }
            double[][] residual = identity(4);
            for (int row = 0; row < 4; row++) {
                residual[row][positionIndex] -= gain[row];
            }
            double[][] joseph = multiply(multiply(residual, covariance), transpose(residual));
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < 4; column++) {
                    joseph[row][column] += gain[row] * config.measurementVariance() * gain[column];
                }
            }
            covariance = joseph;
        }

        private static double[] multiply(double[][] left, double[] right) {
            double[] result = new double[left.length];
            for (int row = 0; row < left.length; row++) {
                for (int column = 0; column < right.length; column++) {
                    result[row] += left[row][column] * right[column];
                }
            }
            return result;
        }

        private static double[][] multiply(double[][] left, double[][] right) {
            double[][] result = new double[left.length][right[0].length];
            for (int row = 0; row < left.length; row++) {
                for (int column = 0; column < right[0].length; column++) {
                    for (int inner = 0; inner < right.length; inner++) {
                        result[row][column] += left[row][inner] * right[inner][column];
                    }
                }
            }
            return result;
        }

        private static double[][] transpose(double[][] matrix) {
            double[][] result = new double[matrix[0].length][matrix.length];
            for (int row = 0; row < matrix.length; row++) {
                for (int column = 0; column < matrix[row].length; column++) {
                    result[column][row] = matrix[row][column];
                }
            }
            return result;
        }

        private static double[][] add(double[][] left, double[][] right) {
            double[][] result = new double[left.length][left[0].length];
            for (int row = 0; row < left.length; row++) {
                for (int column = 0; column < left[row].length; column++) {
                    result[row][column] = left[row][column] + right[row][column];
                }
            }
            return result;
        }

        private static double[][] identity(int size) {
            double[][] result = new double[size][size];
            for (int index = 0; index < size; index++) {
                result[index][index] = 1.0;
            }
            return result;
        }
    }

    private static double[] highPrecisionCoefficients(IouKalmanConfig config, double delta) {
        MathContext context = new MathContext(50, RoundingMode.HALF_EVEN);
        BigDecimal beta = BigDecimal.valueOf(config.beta());
        BigDecimal duration = BigDecimal.valueOf(delta);
        BigDecimal z = beta.multiply(duration, context);
        BigDecimal e = exp(z.negate(), context);
        BigDecimal oneMinusE = BigDecimal.ONE.subtract(e, context);
        BigDecimal betaSquared = beta.multiply(beta, context);
        BigDecimal sigmaSquared = BigDecimal.valueOf(config.sigma()).pow(2, context);
        BigDecimal oneMinusESquared = BigDecimal.ONE.subtract(e.multiply(e, context), context);
        BigDecimal q00Inner =
                duration.subtract(
                                oneMinusE.multiply(BigDecimal.TWO, context).divide(beta, context),
                                context)
                        .add(
                                oneMinusESquared.divide(
                                        beta.multiply(BigDecimal.TWO, context), context),
                                context);
        BigDecimal q00 = sigmaSquared.divide(betaSquared, context).multiply(q00Inner, context);
        BigDecimal q01 =
                sigmaSquared
                        .multiply(oneMinusE.multiply(oneMinusE, context), context)
                        .divide(betaSquared.multiply(BigDecimal.TWO, context), context);
        BigDecimal q11 =
                sigmaSquared
                        .multiply(oneMinusESquared, context)
                        .divide(beta.multiply(BigDecimal.TWO, context), context);
        return new double[] {
            oneMinusE.divide(beta, context).doubleValue(),
            e.doubleValue(),
            q00.doubleValue(),
            q01.doubleValue(),
            q11.doubleValue()
        };
    }

    private static BigDecimal exp(BigDecimal value, MathContext context) {
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        for (int divisor = 1; divisor <= 80; divisor++) {
            term = term.multiply(value, context).divide(BigDecimal.valueOf(divisor), context);
            sum = sum.add(term, context);
        }
        return sum;
    }
}
