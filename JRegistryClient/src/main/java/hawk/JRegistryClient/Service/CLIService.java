package hawk.JRegistryClient.Service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import hawk.JRegitstryCore.RPC.CLIRequest;
import hawk.JRegistryClient.SSH.UserInputHandler.UserInputHandler;
import hawk.JRegistryClient.network.CLIClient;
import org.springframework.beans.factory.annotation.Autowired;


@Service
@Slf4j
@Data
public class CLIService {

    private List<UserInputHandler> userInputHandlers = new ArrayList<>();
    
    @Autowired
    private CLIClient cliClient;


    public String userInputCheck(String input){
        if (input.isEmpty()) {
            log.info("user input is empty");
            return "invalid cmd";
        }
        if(!input.matches("[A-Za-z0-9. ]+")){
            log.info("user input is empty");
            return "invalid cmd";
        }

        String[] cmd = input.split(" ");
        if(cmd.length != 3){
            log.info("user input is empty");
            return "invalid cmd";
        }
        if(!cmd[0].equals("get")&&!cmd[0].equals("set")&&!cmd[0].equals("delete")){
            log.info("invalid cmd");
            return "invalid cmd";
        }


        CLIRequest cliRequest = new CLIRequest();
        cliRequest.setType(cmd[0]);
        cliRequest.setKey(cmd[1]);
        cliRequest.setData(cmd[2].getBytes());
        log.info("userInputCheck before sendRequest: requestId={}", cliRequest.getUuid());
        String response = cliClient.sendRequest(cliRequest);
        log.info("userInputCheck after sendRequest: requestId={}, response={}", cliRequest.getUuid(), response);

        return response;
    }
    

    public void userInputHandlerChain(String input){
        for (UserInputHandler handler : userInputHandlers) {
            input = handler.handle(input);
        }
    }
}