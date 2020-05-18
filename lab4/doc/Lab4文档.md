# Lab4文档

### Part I: Map/Reduce input and output

* `doMap()`
  * 检查inFile是否存在，若不存在则报错并返回；
  * 调用`FileUtils.readFileToString`读出文件内容，编码使用UTF-8；
  * 调用`mapF.map`获得map后的结果；
  * 将map的结果分配到各个reduce任务：对于每一个reduce任务，由0~`nReduce`进行编号；遍历调用map获得的KeyValue list，比较key的哈希取余后的结果和当前reduce任务的编号，若相同，则将该KeyValue作为加入到一个临时的list中；
  * 调用`JSON.toJSONString`将获得的KeyValue list转化为JSON格式的字符串，使用`FileWriter`写入文件名为`Utils.reduceName(jobName, mapTask, i)`的中间文件。
* `doReduce()`
  * 对于编号0~nMap的每一个map任务，读出对应中间文件的内容，调用`JSON.parseArray`获得map任务所得到的类型为`List<KeyValue>`的结果；
  * 遍历该list，加入到`HashMap<String, List<String>> reduceKv`的数据结构中（该数据结构存储key到具有相同key的value列表的映射），哈希映射能够提高查找性能，便于在便利时查找到对应项并将新的字符串append到list中；
  * 为了使得最终reduce的结果按照键排序，使用了一个TreeMap结构`TreeMap<String, String> reduceResult`，TreeMap会按照其键进行排序；
  * 对于`reduceKv`中每一个键值对，将其键值传入`reduceF.reduce`进行reduce的计算，将结果存入上述`reduceResult`中；
  * 调用`JSON.toJSONString`将获得的`reduceResult`转化为JSON格式的字符串，使用`FileWriter`写入文件名为`outfile`的中间文件。

### Part II: Single-worker word count

* `mapFunc`: 使用`[a-zA-Z0-9]+`为word的正则表达式，调用`Pattern.compile`和`p.matcher`获得文本匹配的结果`Matcher m`。调用`m.find()`和`m.group()`获得文中所有单词，将(单词，"1")的构建为KeyValue并加入返回值的list中（其中"1"表示该单词在文中出现了一次）。
* `reduceFunc`: 遍历传入的字符串数组`values`，调用`Integer.valueOf(value)`获得单词所对应的出现次数，累加得到单词出现的总次数并返回。

word count测试结果如下：

![截屏2020-05-18 下午9.46.18](/Users/pro/Desktop/截屏2020-05-18 下午9.46.18.png)

### Part III: Distributing MapReduce tasks

对于0~`nTasks`中的每一个任务，创建其rpc参数对象`new DoTaskArgs(jobName, mapFiles[i], phase, i, nOther)`，创建新的线程并启动。线程中调用`registerChan.read()`获得一个worker的地址，调用`Call.getWorkerRpcService(addr).doTask(doTaskArgs)`向worker发起rpc请求。

线程的停止：主线程创建`new CountDownLatch(nTasks)`，调用`latch.await()`等待所有线程完成；在每一个线程的worker完成并返回后，调用`latch.countDown()`；当该CountDownLatch减为0时，所有线程已经执行完毕，此时主线程继续执行。

对`Channel<String> registerChan`的操作：字符串的Channel类似于队列，在read后会从中取出一个元素。考虑到worker在完成当前任务后可以继续等待执行其他任务，因此每一个线程在worker返回后调用`registerChan.write(addr)`将worker地址加回到Channel中。

### Part IV: Handling worker failures

在每一个线程中加入`while(true)`循环，用于在抛出异常时重新执行。利用try catch语句块获取并处理异常。主要处理如下异常：

* `registerChan.read()`抛出的`InterruptedException`：打印异常，并继续执行循环体；
* `Call.getWorkerRpcService(addr).doTask(doTaskArgs)`抛出的`SofaRpcException`：打印异常，调用`registerChan.write(addr)`将worker地址加回到Channel中，并继续执行循环体。

通过以上异常处理和循环实现了出错则重发的机制，保证了在worker出现failure时系统仍能正常运行。

### Part V: Inverted index generation (optional, as bonus)

* `mapFunc()`
  * 类似于WordCount的实现，以"[a-zA-Z0-9]+"为word的正则表达式，调用`Pattern.compile`和`Pattern.matcher`获得匹配结果`Matcher`
  * 维护`List<KeyValue> kv`存储单词到文件的键值对
  * 维护`HashSet<String> keys`，每次文件有新的单词加入`kv`时，将key加入`keys`；每次遍历到单词时，查找该HashSet，若已经存在，则不将该单词加入`keys`。这样能防止单词在文件中多次出现导致重复的键值对被加入返回值的情况。
* `reduceFunc()`
  * 调用`Arrays.sort(values)`对`values`即单词所在的文件名进行排序
  * 通过values.length获得文件数量
  * 调用`String.join(",", values)`将文件名以逗号隔开进行拼接
  * 按照文档要求返回结果

Inverted index测试结果如下：

![截屏2020-05-18 下午9.59.28](/Users/pro/Library/Application Support/typora-user-images/截屏2020-05-18 下午9.59.28.png)

### MRTest

运行结果如下：

![截屏2020-05-18 下午10.01.24](/Users/pro/Library/Application Support/typora-user-images/截屏2020-05-18 下午10.01.24.png)