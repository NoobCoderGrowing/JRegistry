package io.github.noobcodergrowing.jregistryclient;

import io.github.noobcodergrowing.jregistryclient.JRegistryClient;
import io.github.noobcodergrowing.jregistrycore.KV;
import io.github.noobcodergrowing.jregistrycore.Pair;
import java.lang.InterruptedException;
import java.util.List;

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
            client.set("app/config/name", "demo".getBytes(), "string");
            client.set("app/config/port", "8080".getBytes(), "string");
            Thread.sleep(1000);
            Pair<byte[], String> result = client.get("app/config/name");
            if (result != null) {
                System.out.println("value=" + new String(result.getLeft()));
            } else {
                System.out.println("get returned null");
            }

            //递归列出 app 下所有带值的 key:value 对，key 为绝对完整路径
            List<KV> kvs = client.listAll("app");
            if (kvs == null) {
                System.out.println("listAll returned null (path not found)");
            } else {
                for (KV kv : kvs) {
                    System.out.println(kv.getKey() + "=" + new String(kv.getData())
                            + " (" + kv.getDataType() + ")");
                }
            }
        } finally {
            client.shutdown();
        }
    }
}
