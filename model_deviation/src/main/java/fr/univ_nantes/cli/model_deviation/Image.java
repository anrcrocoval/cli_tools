package fr.univ_nantes.cli.model_deviation;

import plugins.fr.univ_nantes.ec_clem.ec_clem.fiducialset.dataset.point.Point;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class Image {
    private BufferedImage bufferedImage;
    private Graphics2D graphics2D;
    private int width;
    private int height;

    public Image(int width, int height) {
        this.width = width;
        this.height = height;
        bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        graphics2D = bufferedImage.createGraphics();
        graphics2D.setColor(Color.GRAY);
        graphics2D.fillRect(0, 0, width, height);
    }

    public Shape center(Shape shape, Point center) {
        Point minus = center.minus(new Point(new double[]{(double) this.width / 2, (double) this.height / 2}));
        return AffineTransform.getTranslateInstance(-minus.get(0), -minus.get(1)).createTransformedShape(shape);
    }

    public void draw(Shape shape, Color color) {
        synchronized(this) {
            setColor(color);
            graphics2D.draw(shape);
        }
    }

    public void fill(Shape shape, Color color) {
        synchronized(this) {
            setColor(color);
            graphics2D.fill(shape);
        }
    }

    public synchronized void write(Path path) {
        graphics2D.dispose();
        try {
            ImageIO.write(bufferedImage, "png", path.toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setColor(Color color) {
        graphics2D.setColor(color);
    }
}
