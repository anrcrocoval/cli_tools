package fr.univ_nantes.stats.model_deviation;

public class SimulationResult {
    private boolean isAffineEllipseContainsPoint;
    private boolean isRigidEllipseContainsPoint;
    private boolean isAnisotropicRigidEllipseContainsPoint;
    private boolean isTrueEllipseContainsPoint;

    private double affineArea;
    private double rigidArea;
    private double anisotropicRigidArea;
    private double trueArea;

    public SimulationResult(boolean isAffineEllipseContainsPoint, boolean isRigidEllipseContainsPoint, boolean isAnisotropicRigidEllipseContainsPoint, boolean isTrueEllipseContainsPoint, double affineArea, double rigidArea, double anisotropicRigidArea, double trueArea) {
        this.isAffineEllipseContainsPoint = isAffineEllipseContainsPoint;
        this.isRigidEllipseContainsPoint = isRigidEllipseContainsPoint;
        this.isAnisotropicRigidEllipseContainsPoint = isAnisotropicRigidEllipseContainsPoint;
        this.isTrueEllipseContainsPoint = isTrueEllipseContainsPoint;
        this.affineArea = affineArea;
        this.rigidArea = rigidArea;
        this.anisotropicRigidArea = anisotropicRigidArea;
        this.trueArea = trueArea;
    }

    public boolean isAffineEllipseContainsPoint() {
        return isAffineEllipseContainsPoint;
    }

    public boolean isRigidEllipseContainsPoint() {
        return isRigidEllipseContainsPoint;
    }

    public boolean isAnisotropicRigidEllipseContainsPoint() {
        return isAnisotropicRigidEllipseContainsPoint;
    }

    public boolean isTrueEllipseContainsPoint() {
        return isTrueEllipseContainsPoint;
    }

    public double getAffineArea() {
        return affineArea;
    }

    public double getRigidArea() {
        return rigidArea;
    }

    public double getAnisotropicRigidArea() {
        return anisotropicRigidArea;
    }

    public double getTrueArea() {
        return trueArea;
    }
}
