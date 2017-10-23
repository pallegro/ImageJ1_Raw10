package ij.plugin;

import ij.*;
import ij.io.*;
import ij.process.*;
import java.io.*;

/**
 * Opens raw 10-bit image sequences.
 * <p/>
 * 16 byte header:
 *	4-byte magic: 0x10101010
 *	4-byte number of images
 *	4-byte image height
 *	4-byte image width, which must be a multiple of 4
 * Data:
 *  nslice * height * (width + (width >> 2)) bytes
 *  of packed 10-bit grayscale values.
 */

public class Raw10_Reader_ extends ImagePlus implements PlugIn {

    private int width, height;
    private boolean rawBits;
    private boolean sixteenBits;
    private boolean isColor;
    private boolean isBlackWhite;
    private int maxValue;

    public void run(String arg) {
        OpenDialog od = new OpenDialog("Raw10 Reader...", arg);
        String directory = od.getDirectory();
        String name = od.getFileName();
        if (name == null)
            return;
        String path = directory + name;

        IJ.showStatus("Opening: " + path);
        ImageStack stack;
        try {
            stack = openFile(path);
        }
        catch (IOException e) {
            String msg = e.getMessage();
            IJ.showMessage("Raw10 Reader", msg.equals("") ? "" + e : msg);
            return;
        }
        setStack(name, stack);
        FileInfo fi = new FileInfo();
        fi.fileFormat = FileInfo.RAW;//????
        fi.directory = directory;
        fi.fileName = name;
        setFileInfo(fi);
        if (arg.equals("")) show();
    }

    public ImageStack openFile(String path) throws IOException {
        InputStream s = new new FileInputStream(path);
        try {
			final int magic = s.read() | s.read() << 8 | s.read() << 16 | s.read() << 24;
			if (magic != 0x10101010) { IJ.showMessage("Raw10 Reader", "Invalid magic"); return; }
			final int nslice = s.read() | s.read() << 8 | s.read() << 16 | s.read() << 24;
			final int height = s.read() | s.read() << 8 | s.read() << 16 | s.read() << 24;
			final int width  = s.read() | s.read() << 8 | s.read() << 16 | s.read() << 24;
			if (nslice > 1023 | height > 65355 | width > 65355) {
				IJ.showMessage("Raw10 Reader", "Invalid image dimensions"); return;
			}
			ImageStack stack = new ImageStack(width, height);
			final int width_bytes = width + (width >> 2); //10-bits / 8 bits/byte
			final int npixels = width * height;
			final byte[] buf = new byte[width_bytes];
			for (int slice=0; slice<nslice; slice++) {
				byte[] slice = new byte[npixels];
				for (int i=0; i < npixels; ) {
					s.read(buf);
					for (int j=0; j < width_bytes; j+=5, i+=4) {
						//2:10, 12:20, 22:30, 32:40
						slice[i+0] = (byte)((buf[j+0] >> 2) | ((buf[j+1] << 6) & 255));
						slice[i+1] = (byte)((buf[j+1] >> 4) | ((buf[j+2] << 4) & 255));
						slice[i+2] = (byte)((buf[j+2] >> 6) | ((buf[j+3] << 2) & 255));
						slice[i+3] = buf[j+4];
					}
				}
				stack.addSlice("", slice);
			}
		} finally {
            if (s!=null) s.close();
        }
    }
}
