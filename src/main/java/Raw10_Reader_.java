import ij.*;
import ij.io.*;
import ij.plugin.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
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
        //fi.fileFormat = FileInfo.RAW;//????
        fi.directory = directory;
        fi.fileName = name;
        setFileInfo(fi);
        if (arg.equals("")) show();
    }

    public ImageStack openFile(String path) throws IOException {
        InputStream is = new FileInputStream(path);
        try {
			final byte[] hdr = new byte[16];
			is.read(hdr);
			final int magic  = hdr[ 0] | (hdr[ 1]<<8) | (hdr[ 2]<<16) | (hdr[ 3]<<24),
			          nslice = hdr[ 4] | (hdr[ 5]<<8) | (hdr[ 6]<<16) | (hdr[ 7]<<24),
					  height = hdr[ 8] | (hdr[ 9]<<8) | (hdr[10]<<16) | (hdr[11]<<24),
					  width  = hdr[12] | (hdr[13]<<8) | (hdr[14]<<16) | (hdr[15]<<24);
			if (magic != 0x10101010 | nslice > 65355 | height > 65355 | width > 65355)
				throw new IOException("Invalid Raw10 image");
			ImageStack stack = new ImageStack(width, height); //, nslice);
			final int row_bytes = width + (width >> 2);
			final int npixels = width * height;
			final byte[] buf = new byte[row_bytes];
			for (int slice_idx=0; slice_idx < nslice; slice_idx++) {
			//	byte[] slice = new byte[npixels];
				short[] slice = new short[npixels];
				for (int i=0; i < npixels; ) {
					//is.read(buf);
					int count = 0;
					while (count < row_bytes && count >= 0)
						count = is.read(buf, count, row_bytes - count);
					if (count != row_bytes)
						throw new IOException("Error reading from " + path);
					for (int j=0; j < row_bytes; j+=5, i+=4) {
					/*	//2:10, 12:20, 22:30, 32:40
						//Loop around shift?? Really?!?
						slice[i+0] = (byte)(((buf[j+0] & 0xFC) >> 2) | ((buf[j+1] & 0x03) << 6));
						slice[i+1] = (byte)(((buf[j+1] & 0xF0) >> 4) | ((buf[j+2] & 0x0F) << 4));
						slice[i+2] = (byte)(((buf[j+2] & 0xC0) >> 6) | ((buf[j+3] & 0x3F) << 2));
						slice[i+3] =          buf[j+4]; */
						//0:10, 10:20, 20:30, 30:40 -> 6:16
						int x0=buf[j+0], x1=buf[j+1], x2=buf[j+2], x3=buf[j+3], x4=buf[j+4];
						slice[i+0] = (short)(((x0 & 0xFF) << 6) | ((x1 & 0x03) << 14));
						slice[i+1] = (short)(((x1 & 0xFC) << 4) | ((x2 & 0x0F) << 12));
						slice[i+2] = (short)(((x2 & 0xF0) << 2) | ((x3 & 0x3F) << 10));
						slice[i+3] = (short)(( x3 & 0xC0      ) | ((x4 & 0xFF) <<  8));
					}
				}
				stack.addSlice("", slice);
			}
			return stack;
		} finally {
            if (is!=null) is.close();
        }
    }
}
