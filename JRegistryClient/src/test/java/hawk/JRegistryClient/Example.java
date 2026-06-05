package hawk.JRegistryClient;

import hawk.JRegistryClient.JRegistryClient;
import hawk.JRegitstryCore.Pair;
import java.lang.InterruptedException;

public class Example 
{
    public static void main( String[] args ) throws InterruptedException
    {
        //参数为ip地址，端口，任务超时时间，连接超时时间
        JRegistryClient client = new JRegistryClient("127.0.0.3", 6003, 1000, 5000);
        if (!client.connect()) {
            client.shutdown(); //reconnect forever, need to shutdown manually
        }
        try {
            client.set("app.config.name", "demo".getBytes(), "string");
            Thread.sleep(1000);
            Pair<byte[], String> result = client.get("app.config.name");
            if (result != null) {
                System.out.println("value=" + new String(result.getLeft()));
            } else {
                System.out.println("get returned null");
            }
        } finally {
            client.shutdown();
        }
    }
}
