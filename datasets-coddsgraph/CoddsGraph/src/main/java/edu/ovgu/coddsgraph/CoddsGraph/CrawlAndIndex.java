// // This sample uses the Apache HTTP client from HTTP Components (http://hc.apache.org/httpcomponents-client-ga/)
package edu.ovgu.coddsgraph.CoddsGraph;

import java.io.BufferedReader;
import java.io.File;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class CrawlAndIndex 
{
	private static CrawlAndIndex crawlIndexobj=new CrawlAndIndex();
	private  String vertexFileName;
	private  String edgeFileName;
	private  String authorFileName;
	private  String UpstreamFileName;
	private static  String backUpFN;
	private String subscriptionKeyMaintainingFN;
	public static int totalCount=0;
	public static int count=0;
	private  StringBuilder sb;
	DateTimeFormatter uniqueId_ts = DateTimeFormatter.ofPattern("yyyyMMddhhmmss");
	DateTimeFormatter dtf_ts = DateTimeFormatter.ofPattern("yyyy-MM-ddHH:mm:ss");
	private static  FileWriter vertexFW;
	private static FileWriter edgeFW;
	private static FileWriter upstreamFW;
	private static FileWriter authorFW;
	private static  FileWriter backUpFW;
	private FileWriter subscriptionKeyMaintainingFW;
	private int uniqueIdCounter=0;
	private static String loadFromFilesRequired;
	private static int NUM_HOPS;
	private static int TOTAL_HOPS=2;
	private static Map<String, String[]> IdsOfBatchResult;
	private static Map<String, Integer> IdsOfBatchQuery;
	private static int subscriptionKeyLimit;
	static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH-mm");
	static LocalDateTime now = LocalDateTime.now();
	private static String isFirstCrawl;
	private Long startTimeInMilSec;
	final static Logger logger = Logger.getLogger(CrawlAndIndex.class);
	public static JedisPool pool;
	public static Jedis jedis;
	private String dummyFN;
	private static FileWriter dummyFW;

	public static void main(String[] args) 
	{  	
		crawlIndexobj.startTimeInMilSec = System.currentTimeMillis();	
		isFirstCrawl=args[0];
		subscriptionKeyLimit=Integer.parseInt(args[1]);
		NUM_HOPS=Integer.parseInt(args[2]);
		loadFromFilesRequired=args[3];
		String idsToQuery="";
		IdsOfBatchResult=new HashMap<>();
		IdsOfBatchQuery=new HashMap<>();
		String JSONResult_seed="";
		String JSONResult="";	
		pool = new JedisPool(new JedisPoolConfig(),"localhost",6379,300000);	
		jedis =null;

		try {
			jedis = pool.getResource();   
			System.out.println("Connection to server sucessfully"); 
			System.out.println("Server is running: "+jedis.ping());

			Properties properties = new Properties();
			properties.load(CrawlAndIndex.class.getResourceAsStream("log4j.properties"));
			PropertyConfigurator.configure(properties);

			if(isFirstCrawl.toUpperCase().equals("TRUE")){	
				JSONResult_seed= crawlIndexobj.getData("And(And(Ti='a relational model of data for large shared data banks',Composite(AA.AuN=='e f codd')),Y=1970)" ,"Id,RId,Ti,Y,CC,AA.AuN,AA.AuId,J.JN,J.JId,C.CN,C.CId,S.U,VSN","5","0");			

				logger.debug("Indexing started..Executing first hour crawl");

				JsonObject root = new JsonParser().parse(JSONResult_seed).getAsJsonObject();
				JsonArray jsonarray = root.getAsJsonArray("entities");

				for(JsonElement json:jsonarray){  			
					jedis.sadd(Constants.IDSTOVISIT_IN_CURRENTHOP, json.getAsJsonObject().get("Id").toString());	
				}	
				crawlIndexobj.indexVertex(JSONResult_seed); 	
			}

			if(loadFromFilesRequired.toUpperCase().equals("TRUE")){
				logger.debug("Indexing started..Execution after first crawl");
				crawlIndexobj.loadToListFromFiles(Constants.IDSTOVISIT_IN_CURRENTHOP+".csv", Constants.IDSTOVISIT_IN_CURRENTHOP);
				Path pathofidsToVisitInCurrentHop=Paths.get(Paths.get(".").toAbsolutePath().normalize().toString()+"//idsToVisitInCurrentHop.csv");
				Files.delete(pathofidsToVisitInCurrentHop);

				crawlIndexobj.loadToListFromFiles(Constants.IDSTOVISIT_IN_NEXTHOP+".csv", Constants.IDSTOVISIT_IN_NEXTHOP);
				Path pathofidsToVisitofNextHop=Paths.get(Paths.get(".").toAbsolutePath().normalize().toString()+"//idsToVisitNextHop.csv");
				Files.delete(pathofidsToVisitofNextHop);

				crawlIndexobj.loadToListFromFiles(Constants.IDSVISITED+".csv", Constants.IDSVISITED);
				Path pathofidsVisited=Paths.get(Paths.get(".").toAbsolutePath().normalize().toString()+"//idsVisited.csv");
				Files.delete(pathofidsVisited);

				if(jedis.scard(Constants.IDSTOVISIT_IN_CURRENTHOP)==0){
					moveIdsFromCurrentToNextHop();
				}			  
			}

			ScanParams countParams = new ScanParams().count(100);
			String cursor = redis.clients.jedis.ScanParams.SCAN_POINTER_START; 
			boolean nextHopFinished = false;
			while(!nextHopFinished){
				ScanResult<String> idsOfNextHop = jedis.sscan(Constants.IDSTOVISIT_IN_CURRENTHOP, cursor, countParams);
				List<String> result = idsOfNextHop.getResult();
				Iterator<String> it=result.listIterator();

				while (it.hasNext()) {
					if(!crawlIndexobj.checkForTimeOut()){
						String pair = (String) it.next();
						idsToQuery= idsToQuery+"Id="+ pair.toString()+",";
						//removed -- if count==Constants.TOTAL_ID_COUNT_TOQUERY||
						if (!(it.hasNext())){ 	
							JSONResult= crawlIndexobj.getData("OR("+idsToQuery.substring(0, idsToQuery.length()-1)+")" ,"Id,RId,Ti,Y,CC,AA.AuN,AA.AuId,J.JN,J.JId,C.CN,C.CId,S.U,VSN","500","0");	   
							crawlIndexobj.indexVertex(JSONResult); 
							idsToQuery="";
						}
					}
					else{
						Thread.sleep(1800000);
						crawlIndexobj.startTimeInMilSec = System.currentTimeMillis();	
					}
				}
				cursor = idsOfNextHop.getStringCursor();
				if (cursor.equals("0")){
					if(jedis.scard(Constants.IDSTOVISIT_IN_CURRENTHOP)>0){
						cursor = redis.clients.jedis.ScanParams.SCAN_POINTER_START;
					}else{
					logger.warn("No more IDS to visit in current hop or next hop");
					nextHopFinished = true;
					}
				}
			} 
		}
		catch (Exception e1) {
			printListSizes();
			crawlIndexobj.backUp();
			logger.info("Transaction count during termination - "+subscriptionKeyLimit);
			e1.printStackTrace();
		}	
		printListSizes();
		exitCrawl();    	
		logger.debug("Indexing ends");

	}

	/***
	 * Moves all the items in current hop set to the next hop set
	 */
	private static void moveIdsFromCurrentToNextHop(){
		ScanParams scanParams = new ScanParams().count(10000);
		String cur = redis.clients.jedis.ScanParams.SCAN_POINTER_START; 
		boolean cycleIsFinished = false;
		while(!cycleIsFinished){
			ScanResult<String> scanResult = 
					jedis.sscan(Constants.IDSTOVISIT_IN_NEXTHOP, cur, scanParams);
			List<String> result = scanResult.getResult();


			Iterator<String> it=result.listIterator();
			while(it.hasNext()){	
				String pair = (String) it.next();
				jedis.smove(Constants.IDSTOVISIT_IN_NEXTHOP, Constants.IDSTOVISIT_IN_CURRENTHOP, pair);
			}	

			cur = scanResult.getStringCursor();
			if (cur.equals("0")){
				cycleIsFinished = true;
			}                 
		}	            

	}

	/***
	 * prints the size of the sets to the log before backup
	 */
	private static void printListSizes() {
		logger.debug("Current Hop list :"+ jedis.scard(Constants.IDSTOVISIT_IN_CURRENTHOP));
		logger.debug("Next Hop list :"+ jedis.scard(Constants.IDSTOVISIT_IN_NEXTHOP));
		logger.debug("Visited list :"+ jedis.scard(Constants.IDSVISITED));
	}

	/***
	 * closes all the files and jedis connection 
	 */
	private static void exitCrawl() {
		try {
			vertexFW.close();
			edgeFW.close();
			upstreamFW.close();
			authorFW.close();
			dummyFW.close();
			jedis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/***
	 * Forms the query and gets the data from Microsoft API for the requested query
	 * @param expression - String (query expression)
	 * @param attributes - String 
	 * @param count - String
	 * @param from - String
	 * @return String
	 * @throws Exception - exception from Microsoft API
	 */
	private  String getData(String expression, String attributes, String count,String from) throws Exception {
		HttpClient httpclient = HttpClients.createDefault();       
		String JSONResult=null;

		if(subscriptionKeyCountLimitReached()){		
			crawlIndexobj.backUp();
			exitCrawl();
		}else{
			subscriptionKeyLimit++;
			logger.info("Subscriprion key count - "+ subscriptionKeyLimit);
		}

		try
		{
			URIBuilder builder = new URIBuilder("https://westus.api.cognitive.microsoft.com/academic/v1.0/evaluate");
			builder.setParameter("expr", expression);
			builder.setParameter("model", "latest");
			builder.setParameter("attributes", attributes );
			builder.setParameter("count", count);
			builder.addParameter("offset", from);

			logger.debug("Query - "+builder.toString());
			URI uri = builder.build();
			HttpGet request = new HttpGet(uri);
			request.setHeader("Ocp-Apim-Subscription-Key",Constants.subKey ); // dbe029f01ce145f5a41390c981f3bfc5
			// Request body
			HttpUriRequest reqEntity = request;    
			HttpResponse response = httpclient.execute(reqEntity);
			HttpEntity entity = response.getEntity();

			if (entity != null) 
			{
				JSONResult=EntityUtils.toString(entity);	 

				if (dummyFN == null) {
					dummyFN = "dummy" + dtf.format(now) + ".csv";
				}
				else{

					try {
						dummyFW = new FileWriter(new File(dummyFN), true);
						dummyFW.write(System.getProperty("line.separator"));
						dummyFW.write(JSONResult);
					}

					catch (Exception e)
					{
						logger.info("Exception occured inside getData() - "+ e.getMessage());
						throw e;
					}
				}
			}

		}
		catch (Exception e)
		{
			logger.info("Exception occured inside getData() - "+ e.getMessage());
			throw e;
		}
		return JSONResult;

	}

	/***
	 * Parse and write the result info to the papers.csv file
	 * @param jsonArray - String
	 */
	public void indexVertex(String jsonArray){	
		sb = new StringBuilder();
		String uniqueId_paper;
		int from=0;
		String RId;
		String ReferenceIds = "";
		String JSONResult_edges="";
		int sumCitationCount=0;

		if (vertexFileName == null) {
			vertexFileName = "papers"+".csv";

			try {
				vertexFW = new FileWriter(new File(vertexFileName), true);
				sb.append("UniqueId");
				sb.append(',');
				sb.append("paperID:ID");
				sb.append(',');
				sb.append("Title");
				sb.append(',');
				sb.append("PublishInYear:int");
				sb.append(',');
				sb.append("Journal Id");
				sb.append(',');
				sb.append("Journal Name");
				sb.append(',');
				sb.append("Conference Series Id");
				sb.append(',');
				sb.append("Conference Series Name");
				sb.append(',');
				sb.append("Source URL");
				sb.append(',');
				sb.append("Venue Short Name");
				sb.append(',');
				sb.append("TimestampAdded");
				sb.append(',');
				sb.append("TimestampLastVisited");
				sb.append(',');		
				sb.append("TimestampMod");
				sb.append(',');		
				sb.append(":LABEL");
				vertexFW.write(System.getProperty("line.separator"));
				vertexFW.write(sb.toString());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}			
		}

		JsonObject root = new JsonParser().parse(jsonArray).getAsJsonObject();
		JsonArray jsonarray = root.getAsJsonArray("entities");
		for(JsonElement json:jsonarray){	   			
			if(NUM_HOPS<TOTAL_HOPS){
				RId= "RId="+json.getAsJsonObject().get("Id").toString() ;
				String citationCount=json.getAsJsonObject().get("CC").toString();

				//To get all records for citations >1000
				int citedCount=Integer.parseInt(citationCount);
				int quotient;
				//int remainder;
				if (citedCount>1000){
					if(!ReferenceIds.isEmpty()&&sumCitationCount>0){
						try {
							JSONResult_edges=getData("OR("+ReferenceIds.substring(0, ReferenceIds.length()-1)+")", "Id,RId", "1000","0");						
							crawlIndexobj.indexEdges(ReferenceIds.substring(0, ReferenceIds.length()-1) , JSONResult_edges,true);
							sumCitationCount=0;
							ReferenceIds="";
						} 
						catch (Exception e) {
							printListSizes();
							crawlIndexobj.backUp();
							exitCrawl();
							e.printStackTrace();
							System.exit(0);
						}

					}

					
					quotient=citedCount/1000; 
					for(int i=0;i<=quotient;i++){				
						try {
							JSONResult_edges=getData(RId, "Id,RId", citationCount,Integer.toString(from));								
							crawlIndexobj.indexEdges(json.getAsJsonObject().get("Id").toString() , JSONResult_edges, false);
						} 
						catch (Exception e) {
							printListSizes();
							crawlIndexobj.backUp();
							exitCrawl();
							e.printStackTrace();
							System.exit(0);
						}
						from=from+1000;
					}
				}
				else{
					//make a batch of <= 1000 and place a call to the API
					sumCitationCount=sumCitationCount+citedCount;
					if(sumCitationCount<1000){
						ReferenceIds=ReferenceIds+ RId+",";	
					}
					else{
						try {
							JSONResult_edges=getData("OR("+ReferenceIds.substring(0, ReferenceIds.length()-1)+")", "Id,RId", "1000","0");				
							crawlIndexobj.indexEdges(ReferenceIds.substring(0, ReferenceIds.length()-1) , JSONResult_edges,true);
							sumCitationCount=0;
							ReferenceIds="";
							sumCitationCount=citedCount;
							ReferenceIds=ReferenceIds+ RId+",";
						} catch (Exception e) {
							printListSizes();
							crawlIndexobj.backUp();
							exitCrawl();
							e.printStackTrace();
							System.exit(0);
						}

					}
				}
			}		
			try {
				uniqueId_paper=getUniqueId();
				LocalDateTime now = LocalDateTime.now();
				sb=new StringBuilder();
				sb.append(uniqueId_paper);
				sb.append(',');
				sb.append(json.getAsJsonObject().get("Id"));
				sb.append(',');
				sb.append(json.getAsJsonObject().get("Ti").toString().replaceAll("^\"|\"$", ""));
				sb.append(',');
				sb.append(json.getAsJsonObject().get("Y"));
				sb.append(','); 
				sb.append(json.getAsJsonObject().get("J.JId"));
				sb.append(',');
				sb.append(json.getAsJsonObject().get("J.JN"));
				sb.append(',');
				sb.append(json.getAsJsonObject().get("C.CId"));
				sb.append(',');
				sb.append(json.getAsJsonObject().get("C.CN"));
				sb.append(',');
				sb.append(json.getAsJsonObject().get("S.U"));
				sb.append(',');
				sb.append(json.getAsJsonObject().get("VSN"));
				sb.append(',');
				sb.append(dtf_ts.format(now));
				sb.append(",");
				sb.append(dtf_ts.format(now));
				sb.append(",");
				sb.append(dtf_ts.format(now));
				sb.append(",");
				sb.append("paper");
				vertexFW.write(System.getProperty("line.separator"));
				vertexFW.write(sb.toString());	

				//write log after every write to papers.csv file
				writeToUpstream(operation.CREATE, subject.PAPERS,uniqueId_paper, root.toString());

				//write author details to author.csv
				crawlIndexobj.indexAuthor(json.getAsJsonObject().get("Id").toString(), jsonArray);

				jedis.smove(Constants.IDSTOVISIT_IN_CURRENTHOP, Constants.IDSVISITED, json.getAsJsonObject().get("Id").toString());


			} catch (IOException e) {
				logger.info("Exception occured in indexVertex() - "+e.getMessage());
				printListSizes();
				crawlIndexobj.backUp();
				exitCrawl();
				logger.info("BackUp done due to exception in indexVertex()");	
				System.exit(0);
			}   		
		} 
		logger.debug("Out of for loop, Ref Id - "+ReferenceIds);

		if(!ReferenceIds.isEmpty()&&sumCitationCount>0){
			try {
				JSONResult_edges=getData("OR("+ReferenceIds.substring(0, ReferenceIds.length()-1)+")", "Id,RId", "1000","0"); 
				crawlIndexobj.indexEdges(ReferenceIds, JSONResult_edges,true);

			} catch (Exception e) {
				logger.info("Exception occured in indexVertex() - "+e.getMessage());
				printListSizes();
				crawlIndexobj.backUp();
				exitCrawl();
				System.exit(0);
			}	
		}	

		if(jedis.scard(Constants.IDSTOVISIT_IN_CURRENTHOP)>0){
			//do nothing
		}else{
			NUM_HOPS++;
			moveIdsFromCurrentToNextHop();
		}
	}




	/***
	 *  Adds cites relationship to cites.csv and fills in idsToVisitInNexthop set
	 * @param fromPaperId -String
	 * @param jsonArray - String
	 * @param isBatchResult - boolean (pass true, if batch queried result)
	 */
	public void indexEdges(String fromPaperId,String jsonArray,boolean isBatchResult){	 
		sb = new StringBuilder();	
		String uniqueId_citedby;
		String[] referenceIds;
		if (edgeFileName == null) {
			edgeFileName = "cites123"+".csv";
			try {
				edgeFW = new FileWriter(new File(edgeFileName), true);
				sb=new StringBuilder();
				sb.append(":START_ID");
				sb.append(',');
				sb.append(":END_ID");
				sb.append(',');
				sb.append("UniqueId");
				sb.append(',');
				sb.append("TimeStamp");
				sb.append(',');
				sb.append(":TYPE");
				edgeFW.write(System.getProperty("line.separator"));
				edgeFW.write(sb.toString());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}			
		}

		if(!isBatchResult){
			JsonObject root = new JsonParser().parse(jsonArray).getAsJsonObject();
			JsonArray jsonarray = root.getAsJsonArray("entities");
			for(JsonElement json:jsonarray){
				try {
					if(NUM_HOPS<TOTAL_HOPS){		
						if(!(jedis.sismember(Constants.IDSTOVISIT_IN_CURRENTHOP, json.getAsJsonObject().get("Id").toString()))&&
								(!(jedis.sismember(Constants.IDSVISITED, json.getAsJsonObject().get("Id").toString()))) &&
								(!(jedis.sismember(Constants.IDSTOVISIT_IN_NEXTHOP, json.getAsJsonObject().get("Id").toString())))){
							jedis.sadd(Constants.IDSTOVISIT_IN_NEXTHOP, json.getAsJsonObject().get("Id").toString());
						}
					}	
					uniqueId_citedby=getUniqueId();
					sb=new StringBuilder();	
					sb.append(json.getAsJsonObject().get("Id").toString());
					sb.append(',');
					sb.append(fromPaperId);
					sb.append(',');
					sb.append(uniqueId_citedby);
					sb.append(',');
					DateTimeFormatter dtf_ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
					LocalDateTime now = LocalDateTime.now();
					sb.append(dtf_ts.format(now));
					sb.append(',');
					sb.append("Cites");
					edgeFW.write(System.getProperty("line.separator"));
					edgeFW.write(sb.toString());						
					writeToUpstream(operation.CREATE, subject.CITED_BY, uniqueId_citedby, json.getAsJsonObject().toString());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		else{
			String[] batchIds=fromPaperId.replaceAll("RId=", "").split(",");
			for (String id : batchIds) {
				IdsOfBatchQuery.put(id, 0);
			}
			JsonObject root = new JsonParser().parse(jsonArray).getAsJsonObject();
			JsonArray jsonarray = root.getAsJsonArray("entities");

			for(JsonElement json:jsonarray){
				if(NUM_HOPS<TOTAL_HOPS){		
					if(!(jedis.sismember(Constants.IDSTOVISIT_IN_CURRENTHOP, json.getAsJsonObject().get("Id").toString()))&&
							(!(jedis.sismember(Constants.IDSVISITED, json.getAsJsonObject().get("Id").toString()))) &&
							(!(jedis.sismember(Constants.IDSTOVISIT_IN_NEXTHOP, json.getAsJsonObject().get("Id").toString())))){
						jedis.sadd(Constants.IDSTOVISIT_IN_NEXTHOP, json.getAsJsonObject().get("Id").toString());
					}
				}	

				referenceIds=json.getAsJsonObject().get("RId").toString().replace("[", "").replace("]", "").split(",");
				IdsOfBatchResult.put(json.getAsJsonObject().get("Id").toString(), referenceIds);
			}

			Iterator<Entry<String, String[]>> it = IdsOfBatchResult.entrySet().iterator();

			while (it.hasNext()) {
				Map.Entry pair = (Map.Entry)it.next();
				String paperId=pair.getKey().toString();
				String[] citesId= (String[]) pair.getValue();
				for(int i=0;i<citesId.length;i++){
					if(IdsOfBatchQuery.containsKey(citesId[i].toString())){
						try {
							uniqueId_citedby=getUniqueId();
							sb=new StringBuilder();			   			
							sb.append(paperId);
							sb.append(',');
							sb.append(citesId[i]);
							sb.append(',');
							sb.append(uniqueId_citedby);
							sb.append(',');
							DateTimeFormatter dtf_ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
							LocalDateTime now = LocalDateTime.now();
							sb.append(dtf_ts.format(now));
							sb.append(',');
							sb.append("Cites");
							edgeFW.write(System.getProperty("line.separator"));
							edgeFW.write(sb.toString());						
							writeToUpstream(operation.CREATE, subject.CITED_BY, uniqueId_citedby,paperId+citesId);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} 
				}
			}
		}
	}

	/*
	 * Generates unique ID 
	 */
	public String getUniqueId(){
		return  (uniqueId_ts.format(now)+"-"+"001"+"-"+"01"+"-"+((uniqueIdCounter++)%99));
	}


	/***
	 * Write each author details into files
	 * @param fromPaperId - String
	 * @param jsonArray - String
	 */
	public void indexAuthor(String fromPaperId,String jsonArray){
		sb = new StringBuilder();	
		String authorName=new String();
		String authorId= new String();

		String uniqueId_author;

		if (authorFileName == null) {
			authorFileName = "author"+".csv";
			try {
				authorFW = new FileWriter(new File(authorFileName), true);
				sb.append("UniqueId");
				sb.append(',');
				sb.append("PaperId");
				sb.append(',');
				sb.append("AuthorId");
				sb.append(',');
				sb.append("Author Name");
				authorFW.write(System.getProperty("line.separator"));
				authorFW.write(sb.toString());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}			
		}

		JsonObject root = new JsonParser().parse(jsonArray).getAsJsonObject();
		JsonArray jsonarray = root.getAsJsonArray("entities");
		for(JsonElement json:jsonarray){

			JsonObject author = json.getAsJsonObject();
			JsonArray authorarray = author.getAsJsonArray("AA");

			for(JsonElement author_json:authorarray){
				authorName=author_json.getAsJsonObject().get("AuN").toString().replaceAll("^\"|\"$", "");
				authorId=author_json.getAsJsonObject().get("AuId").toString().replaceAll("^\"|\"$", "");
				//authorNames=authorNames+",";

				//authorNames=authorNames.substring(0,authorNames.length()-1);
				try {
					uniqueId_author=getUniqueId();		
					sb=new StringBuilder();
					sb.append(uniqueId_author);
					sb.append(',');
					sb.append(fromPaperId);
					sb.append(',');
					sb.append(authorId);
					sb.append(',');
					sb.append(authorName);
					authorFW.write(System.getProperty("line.separator"));
					authorFW.write(sb.toString());						
					writeToUpstream(operation.CREATE, subject.AUTHORS, uniqueId_author, author.toString());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}	
		}
	}

	/***
	 * Each time any result is written to any of the output files(papers/cites/authors.csv) it is logged in upstream
	 * @param operation_value -  enum values (CREATE/UPDATE/DELETE)
	 * @param sub_value - enum values (PAPERS/CITES/AUTHORS)
	 * @param uniqueId - String 
	 * @param jsonObj - String (query result)
	 */
	public void writeToUpstream(operation operation_value, subject sub_value, String uniqueId, String jsonObj){	  
		sb = new StringBuilder();			
		if (UpstreamFileName == null) {
			UpstreamFileName = "upstream" + dtf.format(now) + ".csv";
			try {
				upstreamFW = new FileWriter(new File(UpstreamFileName), true);
				sb.append("TimeStamp");
				sb.append(',');
				sb.append("Operation");
				sb.append(',');
				sb.append("Subject");
				sb.append(',');
				sb.append("Target");
				sb.append(',');
				sb.append("Details");
				upstreamFW.write(System.getProperty("line.separator"));
				upstreamFW.write(sb.toString());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}			
		}		
		sb=new StringBuilder();
		sb.append(uniqueId_ts.format(now)+"-"+"001"+"-"+"01"+"-"+((uniqueIdCounter++)%99));
		sb.append(',');
		sb.append(operation_value);
		sb.append(',');
		sb.append(sub_value);
		sb.append(',');
		sb.append(uniqueId);
		sb.append(',');
		sb.append(jsonObj);
		try {
			upstreamFW.write(System.getProperty("line.separator"));
			upstreamFW.write(sb.toString());	
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}					

	} 

	/***
	 * Check for number of transactions < 200k
	 * @return boolean
	 */
	public static boolean subscriptionKeyCountLimitReached(){
		if(subscriptionKeyLimit>=200000){
			logger.info("Ran out of Money!!!, You have completed 200K transactions");
			return true;
		}
		return false;
	}

	/***
	 * Writes all the items in sets to the files during backup
	 * @param fileName - String
	 * @param setname - String
	 */
	private static void writeToFiles(String fileName, String setname){
		try{ 
			backUpFN = fileName;     
			backUpFW = new FileWriter(new File(backUpFN), true);	  
			ScanParams scanParams = new ScanParams().count(100000);
			String cur = redis.clients.jedis.ScanParams.SCAN_POINTER_START; 
			boolean cycleIsFinished = false;
			while(!cycleIsFinished){
				ScanResult<String> scanResult = jedis.sscan(setname, cur, scanParams);
				java.util.List<String> result = scanResult.getResult();    
				Iterator<String> it=result.listIterator();
				while(it.hasNext()){	
					String pair = (String) it.next();
					try {	
						backUpFW.write(pair);
						backUpFW.write(System.getProperty("line.separator"));					
					}

					catch (Exception e)
					{
						throw e;
					}  
				}	

				cur = scanResult.getStringCursor();
				if (cur.equals("0")){
					cycleIsFinished = true;
				}                 
			}	            

		}catch(Exception e){
			e.printStackTrace();
		}
		try {
			backUpFW.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/***
	 * backs up all data in redis sets to file, usually called when exception is raised
	 */
	public void backUp(){		 
		logger.info("NUM_HOPS during Backup -" + NUM_HOPS);
		logger.info("Subscription key count during backup - " + subscriptionKeyLimit);

		writeToFiles(Constants.IDSTOVISIT_IN_CURRENTHOP +".csv", Constants.IDSTOVISIT_IN_CURRENTHOP);
		writeToFiles(Constants.IDSTOVISIT_IN_NEXTHOP+".csv", Constants.IDSTOVISIT_IN_NEXTHOP);
		writeToFiles(Constants.IDSVISITED+".csv", Constants.IDSVISITED);

		if(subscriptionKeyMaintainingFN==null){
			subscriptionKeyMaintainingFN="subscriptionKeyMaintaining"+".csv";
			try {
				subscriptionKeyMaintainingFW= new FileWriter(new File(subscriptionKeyMaintainingFN),true);
				subscriptionKeyMaintainingFW.write("Subscription during backup - " +subscriptionKeyLimit);
				subscriptionKeyMaintainingFW.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}

		}

	}

	/***
	 * loads all the data in files onto the set
	 * @param backUpFile - String
	 * @param redisSetName - String
	 */
	public void loadToListFromFiles(String backUpFile, String redisSetName){
		String filePath = backUpFile;
		String line;
		BufferedReader reader;
		long count=0;
		try {
			reader = new BufferedReader(new FileReader(filePath));
			while ((line = reader.readLine()) != null)  
			{
				count++;
				if(count>999999){
					try {
						Thread.sleep(15000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					count=0;
				}
				String[] parts = line.split(",", 2);
				if (parts.length >= 2)
				{
					String key = parts[0];
					//int value = Integer.parseInt(parts[1]);
					jedis.sadd(redisSetName, key.toString());
					//  System.out.println(jedis.scard(redisSetName));
				} else {
					System.out.println("ignoring line: " + line);
				}


			}

			reader.close();

		} catch (IOException e) {

		}

	}

	/***
	 * Checks if 45 minutes is reached from the start of the crawl
	 * @return boolean
	 */
	public boolean checkForTimeOut(){		 
		long currentTimeInMilSec = System.currentTimeMillis();
		long tDelta = currentTimeInMilSec - startTimeInMilSec;
		long elapsedSeconds = tDelta / 1000;
		int min =(int) (elapsedSeconds/60);
		if(min>=45){
			logger.info("TimeOut!!! - It's been 45 minutes since the start");
			return true;
		}
		return false;
	}

	public enum operation{
		CREATE,
		UPDATE,
		DELETE;
	}

	public enum subject{
		PAPERS,
		CITED_BY,
		AUTHORS;
	}


}