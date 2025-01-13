package com.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import java.io.*;

public class TimeSeriesAnalysis {
    
    public static class TSMapper extends Mapper<LongWritable, Text, Text, DoubleWritable> {
        private Text timestamp = new Text();
        private DoubleWritable closeValue = new DoubleWritable();
        
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString();
            if (line.startsWith("timestamp")) return;
            
            String[] fields = line.split("\t");
            if (fields.length >= 5) {
                timestamp.set(fields[0]);
                try {
                    double close = Double.parseDouble(fields[4]);
                    closeValue.set(close);
                    context.write(timestamp, closeValue);
                } catch (NumberFormatException e) {
                    context.getCounter("Map Counters", "Invalid Records").increment(1);
                }
            }
        }
    }
    
    public static class TSReducer extends Reducer<Text, DoubleWritable, Text, DoubleWritable> {
        private long startTime;
        private MultipleOutputs<Text, DoubleWritable> multipleOutputs;
        
        @Override
        protected void setup(Context context) {
            startTime = System.currentTimeMillis();
            multipleOutputs = new MultipleOutputs<>(context);
        }
        
        @Override
        public void reduce(Text key, Iterable<DoubleWritable> values, Context context) 
                throws IOException, InterruptedException {
            double maxClose = Double.MIN_VALUE;
            
            for (DoubleWritable val : values) {
                maxClose = Math.max(maxClose, val.get());
            }
            
            context.write(key, new DoubleWritable(maxClose));
            multipleOutputs.write(key, new DoubleWritable(maxClose), "maxclose/part");
        }
        
        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            long endTime = System.currentTimeMillis();
            long duration = (endTime - startTime) / 1000;
            
            Text statsKey = new Text("STATISTICS");
            Text statsValue = new Text(String.format(
                "Execution Time (seconds): %d\nNumber of Masters: 1\nNumber of Workers: 4",
                duration));
            
            context.write(statsKey, new DoubleWritable(duration));
            
            if (multipleOutputs != null) {
                multipleOutputs.close();
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: TimeSeriesAnalysis <input path> <output path>");
            System.exit(-1);
        }

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Time Series Analysis");
        
        job.setJarByClass(TimeSeriesAnalysis.class);
        job.setMapperClass(TSMapper.class);
        job.setReducerClass(TSReducer.class);
        
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DoubleWritable.class);
        
        MultipleOutputs.addNamedOutput(job, "maxclose", 
            TextOutputFormat.class, Text.class, DoubleWritable.class);
        
        job.setNumReduceTasks(1);
        
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
