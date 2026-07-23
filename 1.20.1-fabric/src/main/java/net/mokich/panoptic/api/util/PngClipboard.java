package net.mokich.panoptic.api.util;

import com.mojang.blaze3d.platform.NativeImage;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PngClipboard {
    private PngClipboard() {}

    public static boolean copy(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage img = NativeImage.read(in);
            int w = img.getWidth();
            int h = img.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    int a = abgr >>> 24;
                    int b = abgr >> 16 & 255;
                    int gr = abgr >> 8 & 255;
                    int r = abgr & 255;
                    out.setRGB(x, y, a << 24 | r << 16 | gr << 8 | b);
                }
            }
            img.close();
            System.setProperty("java.awt.headless", "false");
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(out), null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private record ImageSelection(Image image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            return image;
        }
    }
}