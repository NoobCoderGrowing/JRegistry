package hawk.JRegistryCenter;
import lombok.Data;

import java.util.HashMap;
import java.util.Set;




@Data   
public class BPlusNode{

    private String key;

    private String path;

    private HashMap<String, BPlusNode> children;


    private String value;

  

    public BPlusNode(String key) {
        this.key = key;
        this.children = new HashMap<>();
        
    }

    public BPlusNode(String key, String value) {
        this.key = key;
        this.children = new HashMap<>();
        this.value = value;
    }


    public boolean addNode(BPlusNode newNode) {
        if (children.containsKey(newNode.getKey())) {
            newNode.setPath(path+'/'+newNode.getKey());
            children.put(newNode.getKey(), newNode);
            return true;
        }
        return false;
    }

    public boolean deleteNode(String key){
        if(children.containsKey(key)){
            children.remove(key);
            return true;
        }
        return false;
    }


    public Set<String> ls(){
        return children.keySet();
    }

    public BPlusNode cd(String path){
        if(children.containsKey(path)){
            return children.get(path);
        }
        return null;
    }

    public String pwd(){
        return path;
    }

    

}
