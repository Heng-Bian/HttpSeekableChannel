# HttpSeekableChannel
## Description
HttpSeekableChannel implements SeekableByteChannel based on HTTP Range Requests (see [RFC 7233](https://tools.ietf.org/html/rfc7233)).  
I use it to decompress zip file on the network without downloading.
## Usage
HttpSeekableChannel relies only on the Java standard library  
Just **Copy** and **Paste**
## Example of decompressing zip
```java
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
public class Main(){
    public static void main(String[] args) throws Exception {
        URL url = new URL("https://golang.google.cn/dl/go1.19.1.windows-amd64.zip");
        HttpSeekableChannel httpSeekableChannel = new HttpSeekableChannel(url);
        ZipFile zipFile = new ZipFile(httpSeekableChannel);
        var it = zipFile.getEntries().asIterator();
        while (it.hasNext()) {
            var entry = it.next();
            File target = new File("YOUR_DIR" + entry.getName());
            if (entry.isDirectory()) {
                target.mkdirs();
            } else {
                try (FileOutputStream out = new FileOutputStream(target)) {
                    IOUtils.copy(zipFile.getInputStream(entry), out);
                }
            }
        }
    }
}

```
## Reference
Go httpreader [https://github.com/Heng-Bian/httpreader](https://github.com/Heng-Bian/httpreader)

