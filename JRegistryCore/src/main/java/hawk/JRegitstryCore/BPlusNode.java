package hawk.JRegitstryCore;
import lombok.Data;

import java.util.HashMap;
import java.util.Set;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class BPlusNode {

    private BPlusNode parent;
    private String path;
    private String key;
    private String type;
    private byte[] value;
    private HashMap<String, BPlusNode> children;

    

    public BPlusNode(String key) {
        this.key = key;
        this.children = new HashMap<>();
    }

    public BPlusNode(String key, String path) {
        this.key = key;
        this.path = path;
        this.children = new HashMap<>();
    }

    public BPlusNode(String key, byte[] value, String type) {
        this.key = key;
        this.children = new HashMap<>();
        this.value = value;
        this.type = type;
    }


    public boolean addNodeIfAbsent(BPlusNode newNode) {
        if (!children.containsKey(newNode.getKey())) {
            newNode.setPath(path+'/'+newNode.getKey());
            children.put(newNode.getKey(), newNode);
            newNode.setParent(this);
            return true;
        }
        return false;
    }

    public boolean addNode(BPlusNode newNode) {
        newNode.setPath(path+'/'+newNode.getKey());
        children.put(newNode.getKey(), newNode);
        newNode.setParent(this);
        return true;
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

    public String show(){
         return JSON.toJSONString(this);
    }






}
