package hawk.JRegistryClient;

import java.util.Scanner;
import hawk.JRegistryClient.network.CLIClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired; 
import hawk.JRegistryClient.Service.CLIService;

@SpringBootApplication
public class JRegistryClient {

    
    @Autowired
    private CLIClient CLIClient;

    @Autowired
    private CLIService CLIService;

    public static void main(String[] args) {
        SpringApplication.run(JRegistryClient.class, args);
    }



    @Bean
    public CommandLineRunner cliRunner() {
        return args -> {
            Scanner scanner = new Scanner(System.in);
            System.out.println("JRegistryClient started. Type 'exit' to quit.");

            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();

                if ("exit".equalsIgnoreCase(line)) {
                    System.out.println("Bye.");
                    break;
                }

                // 通过 NettyClient 把命令发给 JRegistry
                CLIClient.send(line);
            }
            scanner.close();
        };
    }

}
