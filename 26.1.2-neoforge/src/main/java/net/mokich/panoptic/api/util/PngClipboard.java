package net.mokich.panoptic.api.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.minecraft.util.Util;

public final class PngClipboard {
    private PngClipboard() {}

    public static boolean copy(Path file) {
        if (Util.getPlatform() == Util.OS.OSX) {
            return copyMac(file);
        }
        return copyAwt(file);
    }

    private static boolean copyMac(Path file) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("panoptic-clip", ".png");
            Files.write(tmp, Files.readAllBytes(file));
            String script = "set the clipboard to (read (POSIX file \""
                    + tmp.toAbsolutePath() + "\") as " + (char) 0x00AB + "class PNGf" + (char) 0x00BB + ")";
            Process p = new ProcessBuilder("osascript", "-e", script)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean copyAwt(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage img = NativeImage.read(in);
            int w = img.getWidth();
            int h = img.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out.setRGB(x, y, img.getPixel(x, y));
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