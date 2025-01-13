package com.hadoop;

import java.io.*;
import java.util.*;

public class TimeSeriesProcessor {
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        
        Map<String, Double> maxCloseValues = new HashMap<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader("data.csv"))) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                
                String[] fields = line.split("\t");
                if (fields.length >= 5) {
                    String timestamp = fields[0];
                    double closeValue = Double.parseDouble(fields[4]);
                    maxCloseValues.merge(timestamp, closeValue, Math::max);
                }
            }
            
            long endTime = System.currentTimeMillis();
            double duration = (endTime - startTime) / 1000.0;
            
            System.out.printf("Total Processing Time: %.3f seconds%n", duration);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

