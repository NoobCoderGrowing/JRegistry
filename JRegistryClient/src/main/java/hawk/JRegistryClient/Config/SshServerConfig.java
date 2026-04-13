package hawk.JRegistryClient.Config;

import hawk.JRegistryClient.network.CLIClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.apache.sshd.server.SshServer;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryClient.Service.CLIService;
@Slf4j
@Configuration
@Data
public class SshServerConfig {
    @Value("${ssh.enabled:true}")
    private boolean enabled;
    @Value("${ssh.host}")
    private String host;
    @Value("${ssh.port}")
    private int port;
    @Value("${ssh.host-key-path}")
    private String hostKeyPath;
    @Value("${ssh.auth.username}")
    private String username;
    @Value("${ssh.auth.password}")
    private String password;
    private SshServer sshd;

    @Autowired
    private CLIService CLIService;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("SSH server disabled by config.");
            return;
        }
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost(host);
        sshd.setPort(port);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get(hostKeyPath)));
        sshd.setPasswordAuthenticator((u, p, s) -> username.equals(u) && password.equals(p));
        sshd.setShellFactory(channel -> new Command() {
            private InputStream in;
            private OutputStream out;
            private ExitCallback exitCallback;
            private volatile boolean running;
            private Thread worker;

            @Override
            public void setInputStream(InputStream in) {
                this.in = in;
            }

            @Override
            public void setOutputStream(OutputStream out) {
                this.out = out;
            }

            @Override
            public void setErrorStream(OutputStream err) {
                // Kept for interface compatibility, errors are written to out for simplicity.
            }

            @Override
            public void setExitCallback(ExitCallback callback) {
                this.exitCallback = callback;
            }

            @Override
            public void start(ChannelSession channelSession, Environment env) {
                running = true;
                worker = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                         BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                        writer.write("JRegistryClient SSH connected. Type 'exit' to quit.\r\n> ");
                        writer.flush();
                        String line;
                        while (running && (line = reader.readLine()) != null) {
                            String cmd = line.trim();
                            if ("exit".equalsIgnoreCase(cmd) || "bye".equalsIgnoreCase(cmd)) {
                                writer.write("Bye.\r\n");
                                writer.flush();
                                break;
                            }
                            String result = CLIService.userInputCheck(cmd);
                            writer.write(result + "\r\n");
                            writer.flush();
                            writer.write("> ");
                            writer.flush();
                        }
                        exitCallback.onExit(0);
                    } catch (IOException e) {
                        log.error("SSH shell error", e);
                        exitCallback.onExit(1, e.getMessage());
                    }
                }, "jregistry-ssh-shell");
                worker.setDaemon(true);
                worker.start();
            }

            @Override
            public void destroy(ChannelSession channelSession) {
                running = false;
                if (worker != null) {
                    worker.interrupt();
                }
            }
        });
        try {
            sshd.start();
            log.info("SSH server started on {}:{}", host, port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start SSH server", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (sshd != null && !sshd.isClosed()) {
            try {
                sshd.stop();
                log.info("SSH server stopped.");
            } catch (IOException e) {
                log.warn("Failed to stop SSH server cleanly", e);
            }
        }
    }
}