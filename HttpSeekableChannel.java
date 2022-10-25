
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;


public class HttpSeekableChannel implements SeekableByteChannel {
    private long position = 0;
    private long size;
    private byte[] headBytes;

    private final URL url;
    private HttpURLConnection connection;
    private InputStream body;
    private String ifRange;
    private final int discard;
    private int count;

    public HttpSeekableChannel(URL url) throws IOException {
        this.url = url;
        this.discard = 1024 * 512;
        init();
    }

    public HttpSeekableChannel(URL url, int discard) throws IOException {
        this.url = url;
        this.discard = discard;
        init();
    }

    private void request() throws IOException {
        if (body != null) {
            body.close();
        }
        if (connection != null) {
            connection.getInputStream().close();
        }
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Range", String.format("bytes=%d-", position));
        connection.setRequestProperty("If-Range", ifRange);
        connection.connect();
        body = connection.getInputStream();
        count++;
        if (connection.getResponseCode() != 206) {
            throw new IOException("not 206 partial content or resource changed");
        }
    }

    private void init() throws IOException {
        if (!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("only support http and https");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Range", "bytes=0-511");
        connection.connect();
        count++;
        byte[] buff = new byte[512];
        try (InputStream in = connection.getInputStream()) {
            int n = in.read(buff);
            byte[] head = new byte[n];
            System.arraycopy(buff, 0, head, 0, n);
            headBytes = head;
        }
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
            throw new IOException(String.format("unexpected response (status %d)", connection.getResponseCode()));
        }
        String range = connection.getHeaderField("Content-Range");
        if (range.contains("/")) {
            size = Long.parseLong(range.split("/")[1]);
            ifRange = validatorFromResponse(connection);
        } else {
            throw new IOException("invalid response header Content-Range " + range);
        }
    }

    private String validatorFromResponse(HttpURLConnection connection) throws IOException {
        String etag = connection.getHeaderField("ETag");
        if (etag != null && !etag.equals("") && etag.charAt(0) == '"') {
            return etag;
        }
        String modifiedTime = connection.getHeaderField("Last-Modified");
        if (modifiedTime != null && !modifiedTime.equals("")) {
            return modifiedTime;
        }
        throw new IOException(url.toString() + " did not offer a strong-enough validator for subsequent requests");
    }

    public byte[] getHeadBytes() {
        return headBytes;
    }

    public int getCount() {
        return count;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (position >= size) {
            return -1;
        }
        if (body == null) {
            request();
        }
        int total = 0;
        int remain = dst.remaining();
        while (total < remain) {
            int n = body.read(dst.array(), dst.position(), dst.remaining());
            if (n >= 0) {
                dst.position(dst.position() + n);
                position = position + n;
                total = total + n;
            } else {
                if (total == 0) {
                    return n;
                } else {
                    break;
                }
            }
        }
        return total;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new IOException("read-only");
    }

    @Override
    public long position() throws IOException {
        return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        if (newPosition == position) {
            return this;
        }
        if (newPosition >= size) {
            throw new IOException("seek beyond end of file");
        }
        if (newPosition < 0) {
            throw new IOException("seek before beginning of file");
        }
        long length = newPosition - position;
        if (length <= discard && length >= 0 && body != null) {
            long n = body.skip(length);
            if (n != length) {
                throw new IOException("skip data error");
            }
            position = newPosition;
        } else {
            position = newPosition;
            request();
        }
        return this;
    }

    @Override
    public long size() throws IOException {
        return size;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        throw new NonWritableChannelException();
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void close() throws IOException {
        if (body != null) {
            body.close();
        }
        if (connection != null) {
            connection.disconnect();
        }
    }
}
