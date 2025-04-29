package org.opencv.core;

public class Moments {
    // Spatial moments
    public double m00, m10, m01, m20, m11, m02, m30, m21, m12, m03;
    
    // Central moments
    public double mu20, mu11, mu02, mu30, mu21, mu12, mu03;
    
    // Central normalized moments
    public double nu20, nu11, nu02, nu30, nu21, nu12, nu03;
    
    public Moments() {
        this(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public Moments(double m00, double m10, double m01, double m20, double m11, double m02, double m30, double m21, double m12, double m03) {
        this(m00, m10, m01, m20, m11, m02, m30, m21, m12, m03, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public Moments(double m00, double m10, double m01, double m20, double m11, double m02, double m30, double m21, double m12, double m03,
            double mu20, double mu11, double mu02, double mu30, double mu21, double mu12, double mu03,
            double nu20, double nu11, double nu02, double nu30, double nu21, double nu12, double nu03) {
        this.m00 = m00;
        this.m10 = m10;
        this.m01 = m01;
        this.m20 = m20;
        this.m11 = m11;
        this.m02 = m02;
        this.m30 = m30;
        this.m21 = m21;
        this.m12 = m12;
        this.m03 = m03;
        this.mu20 = mu20;
        this.mu11 = mu11;
        this.mu02 = mu02;
        this.mu30 = mu30;
        this.mu21 = mu21;
        this.mu12 = mu12;
        this.mu03 = mu03;
        this.nu20 = nu20;
        this.nu11 = nu11;
        this.nu02 = nu02;
        this.nu30 = nu30;
        this.nu21 = nu21;
        this.nu12 = nu12;
        this.nu03 = nu03;
    }

    public Moments(double[] vals) {
        this(vals[0], vals[1], vals[2], vals[3], vals[4], vals[5], vals[6], vals[7], vals[8], vals[9],
                vals[10], vals[11], vals[12], vals[13], vals[14], vals[15], vals[16],
                vals[17], vals[18], vals[19], vals[20], vals[21], vals[22], vals[23]);
    }

    @Override
    public String toString() {
        return "Moments { " +
                "m00=" + m00 + ", m10=" + m10 + ", m01=" + m01 + ", m20=" + m20 + ", m11=" + m11 + ", m02=" + m02 +
                ", m30=" + m30 + ", m21=" + m21 + ", m12=" + m12 + ", m03=" + m03 +
                ", mu20=" + mu20 + ", mu11=" + mu11 + ", mu02=" + mu02 +
                ", mu30=" + mu30 + ", mu21=" + mu21 + ", mu12=" + mu12 + ", mu03=" + mu03 +
                ", nu20=" + nu20 + ", nu11=" + nu11 + ", nu02=" + nu02 +
                ", nu30=" + nu30 + ", nu21=" + nu21 + ", nu12=" + nu12 + ", nu03=" + nu03 + " }";
    }
}