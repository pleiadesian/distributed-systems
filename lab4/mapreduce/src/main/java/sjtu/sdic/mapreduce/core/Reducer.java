package sjtu.sdic.mapreduce.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.io.FileUtils;
import sjtu.sdic.mapreduce.common.KeyValue;
import sjtu.sdic.mapreduce.common.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Created by Cachhe on 2019/4/19.
 */
public class Reducer {

    /**
     * 
     * 	doReduce manages one reduce task: it should read the intermediate
     * 	files for the task, sort the intermediate key/value pairs by key,
     * 	call the user-defined reduce function {@code reduceF} for each key,
     * 	and write reduceF's output to disk.
     * 	
     * 	You'll need to read one intermediate file from each map task;
     * 	{@code reduceName(jobName, m, reduceTask)} yields the file
     * 	name from map task m.
     *
     * 	Your {@code doMap()} encoded the key/value pairs in the intermediate
     * 	files, so you will need to decode them. If you used JSON, you can refer
     * 	to related docs to know how to decode.
     * 	
     *  In the original paper, sorting is optional but helpful. Here you are
     *  also required to do sorting. Lib is allowed.
     * 	
     * 	{@code reduceF()} is the application's reduce function. You should
     * 	call it once per distinct key, with a slice of all the values
     * 	for that key. {@code reduceF()} returns the reduced value for that
     * 	key.
     * 	
     * 	You should write the reduce output as JSON encoded KeyValue
     * 	objects to the file named outFile. We require you to use JSON
     * 	because that is what the merger than combines the output
     * 	from all the reduce tasks expects. There is nothing special about
     * 	JSON -- it is just the marshalling format we chose to use.
     * 	
     * 	Your code here (Part I).
     * 	
     * 	
     * @param jobName the name of the whole MapReduce job
     * @param reduceTask which reduce task this is
     * @param outFile write the output here
     * @param nMap the number of map tasks that were run ("M" in the paper)
     * @param reduceF user-defined reduce function
     */
    public static void doReduce(String jobName, int reduceTask, String outFile, int nMap, ReduceFunc reduceF) {
        try {
            // read the intermediate key/value pairs from intermediate file
            Map<String, List<String>> reduceKv = new HashMap<>();
            for (int i = 0; i < nMap; i++) {
                String intermName = Utils.reduceName(jobName, i, reduceTask);
                File intermFile = new File(intermName);
                if (!intermFile.exists())
                    continue;
                String content = FileUtils.readFileToString(intermFile, "utf-8");
                List<KeyValue> kv = JSON.parseArray(content, KeyValue.class);
                for (KeyValue item : kv) {
                    if (!reduceKv.containsKey(item.key))
                        reduceKv.put(item.key, new ArrayList<>());
                    reduceKv.get(item.key).add(item.value);
                }
            }

            // tree map is sorted by key of intermediate key/value pairs
            Map<String, String> reduceResult = new TreeMap<>();
            reduceKv.forEach((key, values) -> {
                int size = values.size();
                reduceResult.put(key, reduceF.reduce(key, values.toArray(new String[size])));
            });

            // convert to JSON
            String result = JSON.toJSONString(reduceResult);

            // write the reduce output to outFile
            FileWriter fw = new FileWriter(outFile, false);
            fw.write(result);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
