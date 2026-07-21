package io.github.mundanej.map.example.livetrack;

final class IouModel {
    static final int A = 0;
    static final int E = 1;
    static final int Q00 = 2;
    static final int Q01 = 3;
    static final int Q11 = 4;

    private IouModel() {}

    static void coefficients(IouKalmanConfig config, double deltaSeconds, double[] target) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0 || deltaSeconds > 60.0) {
            throw new IllegalArgumentException("deltaSeconds is outside (0, 60]");
        }
        if (target.length < 5) {
            throw new IllegalArgumentException("coefficient target needs five entries");
        }

        double beta = config.beta();
        double sigmaSquared = config.sigma() * config.sigma();
        double z = beta * deltaSeconds;
        double oneMinusE = -StrictMath.expm1(-z);
        double e = 1.0 - oneMinusE;
        target[E] = e;
        if (z < 1.0e-3) {
            double z2 = z * z;
            double z3 = z2 * z;
            double z4 = z3 * z;
            target[A] = deltaSeconds * (1.0 - z / 2.0 + z2 / 6.0 - z3 / 24.0 + z4 / 120.0);
            target[Q00] =
                    sigmaSquared
                            * deltaSeconds
                            * deltaSeconds
                            * deltaSeconds
                            * (1.0 / 3.0
                                    - z / 4.0
                                    + 7.0 * z2 / 60.0
                                    - z3 / 24.0
                                    + 31.0 * z4 / 2520.0);
            target[Q01] =
                    sigmaSquared
                            * deltaSeconds
                            * deltaSeconds
                            * (1.0 / 2.0
                                    - z / 2.0
                                    + 7.0 * z2 / 24.0
                                    - z3 / 8.0
                                    + 31.0 * z4 / 720.0);
            target[Q11] =
                    sigmaSquared
                            * deltaSeconds
                            * (1.0 - z + 2.0 * z2 / 3.0 - z3 / 3.0 + 2.0 * z4 / 15.0);
            return;
        }

        double betaSquared = beta * beta;
        double oneMinusESquared = -StrictMath.expm1(-2.0 * z);
        target[A] = oneMinusE / beta;
        target[Q00] =
                sigmaSquared
                        / betaSquared
                        * (deltaSeconds - 2.0 * oneMinusE / beta + oneMinusESquared / (2.0 * beta));
        target[Q01] = sigmaSquared * oneMinusE * oneMinusE / (2.0 * betaSquared);
        target[Q11] = sigmaSquared * oneMinusESquared / (2.0 * beta);
    }
}
