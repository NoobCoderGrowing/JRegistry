package hawk.JRegistryClient;

import hawk.JRegitstryCore.Pair;

public class TestApp {

    public static void main(String[] args) throws Exception {
        JRegistryClient client = new JRegistryClient("127.0.0.3", 6003, 1000, 5000);
        if (!client.connect()) {
            client.shutdown(); //失败会一直尝试重连，所以需要手动关闭
        }
        try {
            client.set("wenjun", "not right".getBytes(), "string");
            Thread.sleep(1000);
            Pair<byte[], String> result = client.get("wenjun");
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
