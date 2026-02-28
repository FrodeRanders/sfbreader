package se.fk.sfsreader;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Various handy file IO related functions.
 */
public class FileIO {
    private static final Logger log = LoggerFactory.getLogger(FileIO.class);

    /**
     * A nice one: http://thomaswabner.wordpress.com/2007/10/09/fast-stream-copy-using-javanio-channels/
     */
    public static void fastChannelCopy(final ReadableByteChannel src, final WritableByteChannel dest) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);

        while (src.read(buffer) != -1) {
            buffer.flip();
            dest.write(buffer);
            buffer.compact();
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
            dest.write(buffer);
        }
    }

    /**
     * Writes from an InputStream to a file
     */
    public static File writeToFile(InputStream inputStream, File file) throws IOException {

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            ReadableByteChannel inputChannel = Channels.newChannel(inputStream);
            FileChannel fileChannel = raf.getChannel();
            fastChannelCopy(inputChannel, fileChannel);
        }

        return file;
    }

    /**
     * Writes from an InputStream to a temporary file
     */
    public static File writeToTempFile(InputStream inputStream, String prefix, String suffix) throws IOException {

        File file = File.createTempFile(prefix, "." + suffix);
        writeToFile(inputStream, file);
        return file;
    }

    /**
     * Writes from a String to a temporary file
     */
    public static File writeToTempFile(String buf, String prefix, String suffix) throws IOException {

        InputStream is = new ByteArrayInputStream(buf.getBytes("UTF-8"));
        return writeToTempFile(is, prefix, suffix);
    }

    /**
     * Copies a file or a directory (including subdirectories)
     */
    public static void copy(final File src, final File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdir();
            }

            // Copy everything in directory
            String[] children = src.list();
            for (String child : children) {
                copy(new File(src, child), new File(dest, child));
            }
        } else {
            try (ReadableByteChannel in = Channels.newChannel(new FileInputStream(src))) {
                try (WritableByteChannel out = Channels.newChannel(new FileOutputStream(dest))) {
                    fastChannelCopy(in, out);
                }
            }
        }
    }

    /**
     * Removes a file or, if a directory, a directory substructure...
     * <p>
     *
     * @param d a file or a directory
     */
    public static boolean delete(File d) {
        if (null == d || !d.exists())
            return true; // by definition

        if (d.isDirectory()) {
            File[] files = d.listFiles(); // and directories
            if (null != files) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        delete(f);
                    } else {
                        f.delete();
                    }
                }
            }
        }
        return d.delete();
    }

    private static final String USER_AGENT = "Mozilla/5.0";

    public static boolean download(URI uri, File file) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpGet getMethod = new HttpGet(uri);
            getMethod.addHeader("User-Agent", USER_AGENT);

            HttpResponse rawResponse = client.execute(getMethod);
            int status = rawResponse.getStatusLine().getStatusCode();
            if (200 == status) {
                HttpEntity entity = rawResponse.getEntity();
                Header contentType = entity.getContentType();
                log.debug("Download {} from {} [{}]", file.getName(), uri, contentType.getValue());

                writeToFile(entity.getContent(), file);
                return true;

            } else {
                log.warn("Failed to retrieve data from {}: [{}] {}",
                        uri, status, rawResponse.getStatusLine().getReasonPhrase());
            }

            return false;
        }
    }
}
