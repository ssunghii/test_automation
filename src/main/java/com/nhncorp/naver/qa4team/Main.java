package com.nhncorp.naver.qa4team;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.openqa.selenium.server.RemoteControlConfiguration;
import org.openqa.selenium.server.SeleniumServer;

import com.nhncorp.naver.qa4team.regression_test.NanumSwitch;
import com.nhncorp.naver.qa4team.regression_test.ScreenCapturer;
import com.nhncorp.naver.qa4team.regression_test.TestCase;
import com.nhncorp.naver.qa4team.regression_test.TestCaseRunner;
import com.nhncorp.naver.qa4team.regression_test.TestCasesFactory;
import com.thoughtworks.selenium.DefaultSelenium;
import com.thoughtworks.selenium.Selenium;

public class Main {
	public enum Browser{IE, FF}
	enum Font{System, Nanum}
	static private Browser browser = Browser.IE;
	static private Font font = Font.System;
	private List<TestCase> testcases;
	static private String hideDivJS;
	static private String captureForIE;
	static private String targetFolder;
	static private String testURL = "http://search.naver.com/search.naver?where=nexearch&query=";
	static private int port = -1;
	private InputStream fis;
	
	private int getSeleniumPort(){
		if(port == -1){
			port = (int) (2000+Math.random()*3000);
			while(isOpenedPort(port)){
				port = (int) (2000+Math.random()*3000);
			}
		}
		return port;
	}
	
	public static String getBrowserString(){
		return browser.equals(Browser.IE)?"*iexplore":"*firefox";
	}
	
	private boolean isOpenedPort(int port){
		try {
			ServerSocket socket;
			socket = new ServerSocket(port);
			socket.close();
			return false;
		} catch (BindException e) {
			return true;
		}catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	public static String getTargetFolder(){
		if(targetFolder == null){
			SimpleDateFormat format =
		            new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
			targetFolder = String.format("./%s_%s_%s/", browser, font, format.format(new Date()));
			File fold = new File(targetFolder);
			if(!fold.mkdir())
				throw new IllegalStateException("Directory is not created.");
			targetFolder = fold.getAbsolutePath()+"\\";
		}
		return targetFolder;
	}
	
	public static String getTestURL(){
		return testURL;
	}
	
	public static String getStringFromFile(String file){
		InputStream in = ScreenCapturer.class.getClassLoader().getResourceAsStream(file);
		StringWriter writer = new StringWriter();
		try {
			if(in == null){
				in = new FileInputStream(new File("src/main/resources/"+file));
			}
			IOUtils.copy(in, writer);
			in.close();
			writer.close();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return writer.toString();
	}
	public static String getHideDivJS(){
		if(hideDivJS == null){
			hideDivJS = getStringFromFile("hideDiv.js");
		}
		return hideDivJS;
	}
	public static String getCaptureForIE(){
		if(captureForIE == null){
			captureForIE = getStringFromFile("captureForIE.js");
		}
		return captureForIE;
	}
	
	@SuppressWarnings("static-access")
	private boolean parsingArguments(String[] args){
		Options options = new Options();
		Option browser = OptionBuilder.withArgName("browserType")
									.hasArg()
									.withDescription("the browser which it to test(IE or FF)")
									.create("br");
		Option font = OptionBuilder.withArgName("fontType")
									.hasArg()
									.withDescription("the font which it to apply(System or Nanum)")
									.create("ft");
		Option excel = OptionBuilder.withArgName("excelFile")
									.hasArg()
									.withDescription("the excel file which it to use(TestCase.xlsx)")
									.create("xlsx");
		Option url = OptionBuilder.withArgName("url")
									.hasArg()
									.withDescription("the url address which it to test(http://search.naver.com/search.naver?where=nexearch&query=)")
									.create("url");
		Option help = OptionBuilder.withDescription("printing help")
									.create("h");
		options.addOption(browser);
		options.addOption(font);
		options.addOption(excel);
		options.addOption(url);
		options.addOption(help);
		CommandLineParser parser = new GnuParser();
		HelpFormatter formatter = new HelpFormatter();
		try {
	        CommandLine line = parser.parse( options, args );
	        if(line.hasOption("h")){
	        	formatter.printHelp( "xxx.jar", options );
	        	return false;
	        }
	        if( line.hasOption( "br" ) ) {
	        	Main.browser = Browser.valueOf(line.getOptionValue( "br" ));
		    }
	        if( line.hasOption( "ft" ) ) {
	        	Main.font = Font.valueOf(line.getOptionValue( "ft" ));
		    }
	        if( line.hasOption( "xlsx" ) ) {
	        	fis = new FileInputStream(new File(line.getOptionValue( "xlsx" )));
		    } else {
		    	fis = this.getClass().getClassLoader().getResourceAsStream("TestCase.xlsx");
		    }
	        if( line.hasOption( "url" ) ) {
	        	testURL = line.getOptionValue( "url" );
		    }
		} catch( IllegalArgumentException argE ) {
			System.err.println( "Parsing failed.  Reason: " + argE.getMessage() );
			formatter.printHelp( "xxx.jar", options );
	        return false;
	    } catch( ParseException exp ) {
	        System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
			formatter.printHelp( "xxx.jar", options );
	        return false;
	    } catch (FileNotFoundException e) {
	    	System.err.println( "Opening an exel file failed.  Reason: " + e.getMessage() );
			formatter.printHelp( "xxx.jar", options );
	    	return false;
	    }
	    return true;
	}
	
	
	public Main(String[] args) throws IOException{
		if(!parsingArguments(args))
			System.exit(-1);
		testcases = new TestCasesFactory().getTestCases(fis);
		HtmlReporter.getInstance().setTotalTCSize(testcases.size());
	}
	
	public void run() {
		HtmlReporter.setPath(getTargetFolder()+"report.html");
		SeleniumServer sserver;
		try {
			RemoteControlConfiguration conf = new RemoteControlConfiguration();
			conf.setPort(getSeleniumPort());
			sserver = new SeleniumServer(conf);
			sserver.start();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		String browserStr = getBrowserString();
		Selenium selenium = new DefaultSelenium("localhost", getSeleniumPort(), browserStr, "http://www.naver.com");
		selenium.start();
		if(font.equals(Font.System))
			NanumSwitch.offNanum(selenium);
		else NanumSwitch.onNanum(selenium);
		for(TestCase tc:testcases){
			new TestCaseRunner().run(tc, selenium);
		}
	}
	
	public static void main(String[] args) throws Exception {
		new Main(args).run();
	}
	public static Browser getBrowser() {
		return browser;
	}

	public static void setBrowser(Browser br) {
		Main.browser = br;
	}
	
}
