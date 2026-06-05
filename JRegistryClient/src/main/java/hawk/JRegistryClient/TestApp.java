package hawk.JRegistryClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import hawk.JRegitstryCore.Pair;

@SpringBootApplication
public class TestApp {

    public static void main(String[] args) {
        SpringApplication.run(TestApp.class, args);
    }


    @Bean
    CommandLineRunner run() throws Exception {
        return args -> {
            JRegistryClient client = new JRegistryClient("127.0.0.3", 6003, 1000, 5000);
            if(!client.connect()){
                throw new IllegalStateException("无法连接 Registry");
            }
            client.set("wenjun", "not right".getBytes(), "string");
            Thread.sleep(1000);
            Pair<byte[], String> result = client.get("wenjun");
            if(result != null){
                System.out.println("value=" + new String(result.getLeft()));
            }else{
                System.out.println("get returned null");
            }
        };
    }

}
