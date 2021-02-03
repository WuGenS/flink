package com.focus.learning.kafka;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011;
import org.apache.flink.util.Collector;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

/**
 * @ClassName TransactionTopicConsumer
 * @Description
 * @Author samson 005437
 * @Date 2021/2/1 19:53
 * @Version V1.0
 **/
public class TransactionTopicConsumer {

    final static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public static void main(String[] args) throws Exception {

        Properties pros = new Properties();
        pros.put("bootstrap.servers","bigd-dev-hadoop-s185:9092,bigd-dev-hadoop-s186:9092, bigd-dev-hadoop-s188:9092");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
//        env.setMaxParallelism(200);
        FlinkKafkaConsumer011<String> consumer011 = new FlinkKafkaConsumer011<>("transaction",new SimpleStringSchema(),pros);
        KeyedStream<Tuple3<String, String, Long>, Tuple> keyedStream = env.addSource(consumer011)
                .map(new MapFunction<String, Tuple3<String, String, Long>>() {


            @Override
            public Tuple3<String, String, Long> map(String value) throws Exception {
                String[] inputs = value.split(",");
                Tuple3<String, String, Long> tuple3 = new Tuple3<String, String, Long>(inputs[0], inputs[1], format.parse(inputs[2]).getTime());
                return tuple3;
            }
        })
                .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor<Tuple3<String, String, Long>>(Time.seconds(0)) {
            @Override
            public long extractTimestamp(Tuple3<String, String, Long> element) {
                return element.f2;
            }
        }).setParallelism(2).keyBy("f0");

        keyedStream
//                  .window(TumblingEventTimeWindows.of(Time.days(1),Time.hours(-8)))
                .timeWindow(Time.seconds(10),Time.seconds(3))
//                .timeWindow(Time.days(1))
//                .allowedLateness(Time.seconds(2))
                .process(new ProcessWindowFunction<Tuple3<String,String,Long>, Tuple3<String,String,Long>, Tuple, TimeWindow>() {
                    @Override
                    public void process(Tuple tuple, Context context, Iterable<Tuple3<String, String, Long>> elements, Collector<Tuple3<String, String, Long>> out) throws Exception {
                        Long sum = 0L;
                        int order = 1;
                        System.out.println("###当前窗口：( " + format.format(new Date(context.window().getStart())) + "," + format.format(new Date(context.window().getEnd())) + ") #######");
                        Iterator<Tuple3<String, String, Long>> iterator = elements.iterator();
                        while (iterator.hasNext()) {
                            Tuple3<String, String, Long> tuple3 = iterator.next();
                            System.out.println("order:" + order + "::" + tuple3.toString());
                            sum += Long.parseLong(tuple3.f1);
                        }
                        System.out.println("###当前窗口：( " + format.format(new Date(context.window().getStart())) + "," + format.format(new Date(context.window().getEnd())) + ") #######sum::" + sum);
                    }
                }).setParallelism(1);
        env.execute(TransactionTopicConsumer.class.getName());
    }
}
