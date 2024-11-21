package com.logicaldoc.bm;
import java.io.File;
import java.io.IOException;
import java.util.List;

import com.logicaldoc.util.csv.CSVFileReader;


public class Testbench {

	public static void main(String[] args) throws IOException {
		File csv=new File("target/ld_document.csv");
		CSVFileReader reader = new CSVFileReader(csv.getAbsolutePath());
		reader.readFields();
		List<String> fields = reader.readFields();
		for (String field : fields) {
			System.out.println(field);
		}
	}
}