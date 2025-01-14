package com.businesslogic;
import com.hashMap.*;

import org.json.JSONException;
import org.json.simple.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import com.businesslogic.ExtractTradeData;
import com.dao.TradeListDAOImpl;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.pojo.TradeList;

import sun.rmi.runtime.Log;
//import com.dao.*;

public class GenerateHashMap {
	
	ArrayList<ResultObject> result = new ArrayList<ResultObject>(); 
	public HashMap<String, HashMap<Integer, TradeInfo>> past = new HashMap<String, HashMap<Integer, TradeInfo>>(); 
	public HashMap<String, HashMap<Integer, TradeInfo>> future = new HashMap<String, HashMap<Integer, TradeInfo>>(); 
	ArrayList<TradeList> trades = null;
	static int interval = 60;
	int databaseSize;
	//HashMap(TraderID , TradeIDList[])
	HashMap<Integer,ArrayList<Integer>> trackFraudTrades = new HashMap<Integer,ArrayList<Integer>>();
	ArrayList<Integer> fraudulentTransactions;
	
 	
	public GenerateHashMap() {
			this.fraudulentTransactions = new ArrayList<Integer>();
			this.trades = new ExtractTradeData().getDataFromDatabase();
			this.databaseSize = new TradeListDAOImpl().getRecordCount();
//			this.databaseSize = 3;	
			System.out.println("Database Size: "+this.databaseSize);
			//updating the future HashMap
			
			ArrayList<Integer> initialArray = new ArrayList<Integer>();
			int firstTradeId = trades.get(0).getTradeID();
			initialArray.add(firstTradeId);
			trackFraudTrades.put(trades.get(0).getTrader().getTraderID(), initialArray);
			for(int i=1;i<=interval;i++) {
				//String key = trades.get(i).getCompany() + ";" + trades.get(i).getBuyOrSell();
				//Add the incoming trade to HashMap
				
				//Lower Case Key
				String key = generateKey(trades.get(i));
				int traderid =trades.get(i).getTrader().getTraderID();
				int traderQuant = trades.get(i).getQty();
				int tradeid = trades.get(i).getTradeID();
				
				if(trackFraudTrades.containsKey(traderid)) {
					
					ArrayList<Integer> temp;
					temp = trackFraudTrades.get(traderid);
					temp.add(tradeid);
					trackFraudTrades.put(traderid, temp);
					
				}else {
					
					ArrayList<Integer> temp = new ArrayList<>();
					temp.add(tradeid);
					trackFraudTrades.put(traderid, temp);
				}
				
				if(future.containsKey(key)) {
						if(future.get(key).containsKey(traderid)) {
							//update the inside hashmap
							//System.out.println("Aggrgating: "+ traderQuant);
							TradeInfo tradeInfo = future.get(key).get(traderid);
							tradeInfo.quantity += traderQuant;
							tradeInfo.tradeNumberList.add(tradeid);
							future.get(key).put(traderid, tradeInfo);
							
						}else {
							ArrayList<Integer> listOfTrades = new ArrayList<Integer>();
							listOfTrades.add(tradeid);
							TradeInfo tradeInfo = new TradeInfo(traderQuant,listOfTrades);
							future.get(key).put(traderid,tradeInfo);
						
						}
						
					}else {
						
						//traderValue.put(traderid, traderQuant)
						HashMap<Integer, TradeInfo> traderValue = new HashMap<Integer, TradeInfo>();
						ArrayList<Integer> listOfTrades = new ArrayList<Integer>();
						listOfTrades.add(tradeid);
						TradeInfo tradeInfo = new TradeInfo(traderQuant,listOfTrades);
						traderValue.put(traderid, tradeInfo);
						future.put(key , traderValue);
					}
					
			}
//			System.out.println(this.future);
	}
	
	public JSONObject parseDatabase(ArrayList<TradeList> allTrades, HashMap<String, HashMap<Integer,TradeInfo>> past, HashMap<String, HashMap<Integer, TradeInfo>> future) {
		System.out.println(this.databaseSize);
		int current = 0;
		int pastStart = 0;
		int pastEnd = 0;
		int futureStart = 1;
		int futureEnd = interval; // if database size less than 60 then futureEnd will point to the last element in the database
		int pastDataSize = 1;
		int futureDataSize = interval;
		// start from 1 as the trade 0 is added in the "past" hash table 
		for(int i=0; i<this.databaseSize; i++)
		{
						
			TradeList trade = allTrades.get(i);
			System.out.println("Current Trade: "+ trade);
			String key = generateKey(trade);
			System.out.println("---------------Past Data-----------------");
			System.out.println(this.past);
			
			System.out.println("---------------Future Data-----------------");
			System.out.println(this.future);
//			
			
			if(i>=1 && isLargeTrade(trade) ) {
				findFRScenario(trade); 
			}
			
			//update HashTable
			current++;
			if(pastDataSize<=interval) {
				pastEnd++;
				addIntoHashTable(this.past,allTrades.get(pastEnd-1));
				pastDataSize++;
			}
			else {
				//remove pastStart from the table and add current to the table
				
				TradeList tradeToRemove = allTrades.get(pastStart);
				String outerKey = generateKey(tradeToRemove);
				HashMap<Integer, TradeInfo> innerMap = this.past.get(outerKey);
				int	traderId = tradeToRemove.getTrader().getTraderID();
				
				if(innerMap.containsKey(traderId)) {
					
					TradeInfo tradeInfo = innerMap.get(traderId);
					tradeInfo.quantity -= tradeToRemove.getQty();
					tradeInfo.tradeNumberList.remove(tradeInfo.tradeNumberList.indexOf(tradeToRemove.getTradeID()));
					innerMap.put(traderId, tradeInfo);
					if(innerMap.get(traderId).getQuantity()<=0) {
//						System.out.println("Remove Trade traderID: "+traderId);
						innerMap.remove(traderId);
						if(innerMap.keySet().size()==0) {
							this.past.remove(outerKey);
						}
					}
				}
				pastStart++;			
				TradeList tradeToAdd = allTrades.get(current-1);
				outerKey = generateKey(tradeToAdd);
				
				if(this.past.containsKey(outerKey)) {
					int innerKey = tradeToAdd.getTrader().getTraderID();
					if(this.past.get(outerKey).containsKey(innerKey)) {
						
						HashMap<Integer, TradeInfo> inner = this.past.get(outerKey);
						TradeInfo tradeInfo = inner.get(innerKey);
						tradeInfo.quantity+=tradeToAdd.getQty();
						tradeInfo.tradeNumberList.add(tradeToAdd.getTradeID());
						inner.put(innerKey, tradeInfo);
						
					}
					else {						
						HashMap<Integer, TradeInfo> temp = this.past.get(outerKey);
						
						ArrayList<Integer> listOfTrades = new ArrayList<Integer>();
						listOfTrades.add(tradeToAdd.getTradeID());
						TradeInfo tradeInfo = new TradeInfo(tradeToAdd.getQty(), listOfTrades);
						
						temp.put(tradeToAdd.getTrader().getTraderID(), tradeInfo);
					}
								
				}
				else {
					HashMap<Integer, TradeInfo> temp = new HashMap<Integer, TradeInfo>();
					ArrayList<Integer> listOfTrades = new ArrayList<Integer>();
					listOfTrades.add(tradeToAdd.getTradeID());
					TradeInfo tradeInfo = new TradeInfo(tradeToAdd.getQty(), listOfTrades);
					temp.put(tradeToAdd.getTrader().getTraderID(), tradeInfo);
					this.past.put(outerKey, temp);
				}
				
			}
			
			

			//remove pastStart from the table and add current to the table
			if(futureStart < this.databaseSize)
			{
				TradeList futureTradeToRemove = allTrades.get(futureStart);
				String outerKey = generateKey(futureTradeToRemove );
				HashMap<Integer, TradeInfo> futureInnerMap = this.future.get(outerKey);
				int	traderId = futureTradeToRemove.getTrader().getTraderID();
				
				if(futureInnerMap.containsKey(traderId)) {
					
					TradeInfo tradeInfo = futureInnerMap.get(traderId);
					tradeInfo.quantity -= futureTradeToRemove.getQty();
					tradeInfo.tradeNumberList.remove(tradeInfo.tradeNumberList.indexOf(futureTradeToRemove.getTradeID()));
					futureInnerMap.put(traderId, tradeInfo);
					if(futureInnerMap.get(traderId).getQuantity()<=0) {
//						System.out.println("Remove Trade traderID: "+traderId);
						futureInnerMap.remove(traderId);
						if(futureInnerMap.keySet().size()==0) {
							this.future.remove(outerKey);
						}
					}
				}
			
			futureStart++;	
			
			if(futureEnd < databaseSize - 1) {
				TradeList tradeToAdd = allTrades.get(futureEnd+1);
			
				String futureOuterKey = generateKey(tradeToAdd);
				
				if(this.future.containsKey(futureOuterKey)) {
					int innerKey = tradeToAdd.getTrader().getTraderID();
					if(this.future.get(futureOuterKey).containsKey(innerKey)) {
						
						HashMap<Integer, TradeInfo> inner = this.future.get(futureOuterKey);
						TradeInfo tradeInfo = inner.get(innerKey);
						tradeInfo.quantity+=tradeToAdd.getQty();
						tradeInfo.tradeNumberList.add(tradeToAdd.getTradeID());
						inner.put(innerKey, tradeInfo);
						
					}
					else {						
						HashMap<Integer, TradeInfo> temp = this.future.get(futureOuterKey);
						
						ArrayList<Integer> listOfTrades = new ArrayList<Integer>();
						listOfTrades.add(tradeToAdd.getTradeID());
						TradeInfo tradeInfo = new TradeInfo(tradeToAdd.getQty(), listOfTrades);
						
						temp.put(tradeToAdd.getTrader().getTraderID(), tradeInfo);
					}
								
				}
				else {
					HashMap<Integer, TradeInfo> temp = new HashMap<Integer, TradeInfo>();
					ArrayList<Integer> listOfTrades = new ArrayList<Integer>();
					listOfTrades.add(tradeToAdd.getTradeID());
					TradeInfo tradeInfo = new TradeInfo(tradeToAdd.getQty(), listOfTrades);
					temp.put(tradeToAdd.getTrader().getTraderID(), tradeInfo);
					this.future.put(futureOuterKey, temp);
				}

			}
			
			futureEnd++;
			
			}
			
			
			
//			System.out.println("Past Data--------------------------------");
//			System.out.println(this.past);
//			

			// Add into futures HashTable
			

			//remove futureStart from the table and add futureEnd+1 to the table

		}
		System.out.println(result.size());
		JSONArray jsonArray = new JSONArray();
		for (int i=0; i < result.size(); i++) {
		        JSONObject eachData = new JSONObject();
		        try {
					eachData.put("ScenarioNumber", result.get(i).getScenarioNumber());
					eachData.put("ScenarioNumber", result.get(i).getListOfFrontRunningTrades());
					eachData.put("ScenarioNumber", result.get(i).getListOfSuspiciousTrades());
					eachData.put("ScenarioNumber", result.get(i).getCurrentTrade());
					jsonArray.add(i, eachData);
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		JSONObject root =new JSONObject();
		try {
			root.put("data", jsonArray);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return root;
	}
	
	
	private void addIntoHashTable(HashMap<String, HashMap<Integer, TradeInfo>> hashTable, TradeList tradeList) {
		String key = generateKey(tradeList);
		System.out.println(key);
		if(hashTable.containsKey(key))
		{
			HashMap<Integer, TradeInfo> temp = hashTable.get(key); //get inner Hash Table
			int innerKey = tradeList.getTrader().getTraderID();
			if(temp.containsKey(tradeList.getTrader().getTraderID())) { //if trader exists
				
				TradeInfo tradeInfo = temp.get(innerKey);
				ArrayList<Integer> listOfTrades = tradeInfo.getTradeNumberList();
				tradeInfo.quantity += tradeList.getQty();
				listOfTrades.add(tradeList.getTradeID());
				temp.put(innerKey, tradeInfo);
			}
			else { // if not found
				
				ArrayList<Integer> listOfTrades = new ArrayList<Integer>();
				listOfTrades.add(tradeList.getTradeID());
				
				TradeInfo tradeInfo = new TradeInfo(tradeList.getQty(), listOfTrades);

				temp.put(innerKey, tradeInfo);
			}			
		}
		else {
			HashMap<Integer, TradeInfo> innerMap = new HashMap<Integer, TradeInfo>();
			
			ArrayList<Integer> listOfTrades = new ArrayList<Integer>();
			listOfTrades.add(tradeList.getTradeID());
			
			TradeInfo tradeInfo = new TradeInfo(tradeList.getQty(), listOfTrades);
			
			
			innerMap.put(tradeList.getTrader().getTraderID(), tradeInfo);
			hashTable.put(key, innerMap);
		}


	}

//	
	private String generateKey(TradeList tradeList) {
		// TODO Auto-generated method stub
		return (tradeList.getCompany() + ";" + tradeList.getBuyOrSell()+";"+tradeList.getTypeOfSecurity() ).toLowerCase();
	}

	public static boolean isLargeTrade(TradeList trade) {
		
		if(trade.getQty() >= 20000)
			return true;
		return false;
	}
	
	void findFRScenario(TradeList victim) {
		String key1 = generateKey(victim);
		//System.out.println(victim.getBuyOrSell());
		if (victim.getBuyOrSell().equals("Buy") && victim.getTypeOfSecurity().equals("Shares")) {
			
			//System.out.println("Victim is buy");
			String key2 = (victim.getCompany() + ";Sell" + ";Shares").toLowerCase();
			//System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key1);
			HashMap<Integer, TradeInfo> futureTraderMap = future.get(key2);
			if (pastTraderMap != null && futureTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();

					if (futureTraderMap.containsKey(findInFuture)) {
						TradeInfo temp = (TradeInfo) pastTraderEntry.getValue();
						pastSecurities = temp.getQuantity();
						if (futureTraderMap.get(findInFuture).getQuantity() <= (1.1 * pastSecurities) && futureTraderMap.get(findInFuture).getQuantity() >= (0.9 * pastSecurities)) {
							// fraud trade detected
//							this.fraudulentTransactions.add(findInFuture);
							
							System.out.println("Current Trade: "+ victim);
							TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
							System.out.println("Suspicious Trade/s: "+ tradeInfo.getTradeNumberList());
							System.out.println("Front running detected at: "+ futureTraderMap.get(findInFuture).getTradeNumberList());
							result.add(new ResultObject(victim, tradeInfo , futureTraderMap.get(findInFuture), 1));
						}

					}
				}
			}
			
		}

		if (victim.getBuyOrSell().equals("Buy") && victim.getTypeOfSecurity().equals("Futures")) {
			
			//System.out.println("Victim is buy");
			String key2 = (victim.getCompany() + ";Sell" + ";Futures").toLowerCase();
			//System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key1);
			HashMap<Integer, TradeInfo> futureTraderMap = future.get(key2);
			if (pastTraderMap != null && futureTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();

					if (futureTraderMap.containsKey(findInFuture)) {
						TradeInfo temp = (TradeInfo) pastTraderEntry.getValue();
						pastSecurities = temp.getQuantity();
						if (futureTraderMap.get(findInFuture).getQuantity() <= (1.1 * pastSecurities) && futureTraderMap.get(findInFuture).getQuantity() >= (0.9 * pastSecurities)) {
							// fraud trade detected
//							this.fraudulentTransactions.add(findInFuture);
							System.out.println("Current Trade: "+ victim);
							TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
							System.out.println("Suspicious Trade/s: "+ tradeInfo.getTradeNumberList());
							System.out.println("Front running detected at: "+ futureTraderMap.get(findInFuture).getTradeNumberList());
							result.add(new ResultObject(victim, tradeInfo , futureTraderMap.get(findInFuture), 2));
						}

					}
				}
			}
			
		}
		
		if (victim.getBuyOrSell().equals("Buy") && victim.getTypeOfSecurity().equals("Shares")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Buy" + ";Futures").toLowerCase();
			String key2 = (victim.getCompany() + ";Sell" + ";Futures").toLowerCase();
			//System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			HashMap<Integer, TradeInfo> futureTraderMap = future.get(key2);
			if (pastTraderMap != null && futureTraderMap != null) {
				Integer findInFuture, pastSecurities;
				
				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();

					if (futureTraderMap.containsKey(findInFuture)) {
						TradeInfo temp = (TradeInfo) pastTraderEntry.getValue();
						pastSecurities = temp.getQuantity();
						if (futureTraderMap.get(findInFuture).getQuantity() <= (1.1 * pastSecurities) && futureTraderMap.get(findInFuture).getQuantity() >= (0.9 * pastSecurities)) {
							// fraud trade detected
//							this.fraudulentTransactions.add(findInFuture);
							System.out.println("Current Trade: "+ victim);
							TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
							System.out.println("Suspicious Trade/s: "+ tradeInfo.getTradeNumberList());
							System.out.println("Front running detected at: "+ futureTraderMap.get(findInFuture).getTradeNumberList());
							result.add(new ResultObject(victim, tradeInfo , futureTraderMap.get(findInFuture), 3));
						}

					}
				}
			}
			
		}
		
		if (victim.getBuyOrSell().equals("Buy") && victim.getTypeOfSecurity().equals("Futures")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Buy" + ";Shares").toLowerCase();
			String key2 = (victim.getCompany() + ";Sell" + ";Shares").toLowerCase();
			System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			HashMap<Integer, TradeInfo> futureTraderMap = future.get(key2);
			if (pastTraderMap != null && futureTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();

					if (futureTraderMap.containsKey(findInFuture)) {
						TradeInfo temp = (TradeInfo) pastTraderEntry.getValue();
						pastSecurities = temp.getQuantity();
						if (futureTraderMap.get(findInFuture).getQuantity() <= (1.1 * pastSecurities) && futureTraderMap.get(findInFuture).getQuantity() >= (0.9 * pastSecurities)) {
							// fraud trade detected
//							this.fraudulentTransactions.add(findInFuture);
							System.out.println("Current Trade: "+ victim);
							TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
							System.out.println("Suspicious Trade/s: "+ tradeInfo.getTradeNumberList());
							System.out.println("Front running detected at: "+ futureTraderMap.get(findInFuture).getTradeNumberList());
							result.add(new ResultObject(victim, tradeInfo , futureTraderMap.get(findInFuture), 4));
						}

					}
				}
			}
			
		}

		
		// sb sp
		if (victim.getBuyOrSell().equals("Buy") && victim.getTypeOfSecurity().equals("Shares")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Sell" + ";Put Option").toLowerCase();
			//System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			if (pastTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();
					//this.fraudulentTransactions.add(findInFuture);
					System.out.println("Current Trade: "+ victim);
					TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
					System.out.println("Front Running Detected: "+ tradeInfo.getTradeNumberList());			
					result.add(new ResultObject(victim, new TradeInfo() , tradeInfo, 5));
				}
			}
			
		}
		
		// sb fp
		if (victim.getBuyOrSell().equals("Buy") && victim.getTypeOfSecurity().equals("Futures")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Sell" + ";Put Option").toLowerCase();
			//System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			if (pastTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();
					//this.fraudulentTransactions.add(findInFuture);
					System.out.println("Current Trade: "+ victim);
					TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
					System.out.println("Front Running Detected: "+ tradeInfo.getTradeNumberList());			
					result.add(new ResultObject(victim, new TradeInfo() , tradeInfo, 6));
				}
			}
		}
		
		// bb sc
		if (victim.getBuyOrSell().equals("Buy") && victim.getTypeOfSecurity().equals("Shares")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Buy" + ";Call Option").toLowerCase();
			//System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			if (pastTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();
					//this.fraudulentTransactions.add(findInFuture);
					System.out.println("Current Trade: "+ victim);
					TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
					System.out.println("Front Running Detected: "+ tradeInfo.getTradeNumberList());			
					result.add(new ResultObject(victim, new TradeInfo() , tradeInfo, 7));
				}
			}
		}
		
		// bb fc
		if (victim.getBuyOrSell().equals("Buy") && victim.getTypeOfSecurity().equals("Futures")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Buy" + ";Call Option").toLowerCase();
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			
			if (pastTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();
					//this.fraudulentTransactions.add(findInFuture);
					System.out.println("Current Trade: "+ victim);
					TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
					System.out.println("Front Running Detected: "+ tradeInfo.getTradeNumberList());			
					result.add(new ResultObject(victim, new TradeInfo() , tradeInfo, 8));
				}
			}
		}
		
		//current trade - sell scenarios
		// ssb sss
		if (victim.getBuyOrSell().equals("Sell") && victim.getTypeOfSecurity().equals("Shares")) {
			
			//System.out.println("Victim is buy");
			String key2 = (victim.getCompany() + ";Buy" + ";Shares").toLowerCase();
			System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key1);
			HashMap<Integer, TradeInfo> futureTraderMap = future.get(key2);
			if (pastTraderMap != null && futureTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();

					if (futureTraderMap.containsKey(findInFuture)) {
						TradeInfo temp = (TradeInfo) pastTraderEntry.getValue();
						pastSecurities = temp.getQuantity();
						if (futureTraderMap.get(findInFuture).getQuantity() >= pastSecurities) {
							// fraud trade detected
//							this.fraudulentTransactions.add(findInFuture);
							System.out.println("Current Trade: "+ victim);
							TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
							System.out.println("Suspicious Trade/s: "+ tradeInfo.getTradeNumberList());
							System.out.println("Front running detected at: "+ futureTraderMap.get(findInFuture).getTradeNumberList());
							ResultObject r = new ResultObject(victim, tradeInfo , futureTraderMap.get(findInFuture), 9 );
							System.out.println(r);
							this.result.add(r);
						}

					}
				}
			}
			

		}
		
		
		//ssb fff
		if (victim.getBuyOrSell().equals("Sell") && victim.getTypeOfSecurity().equals("Futures")) {
			
			//System.out.println("Victim is buy");
			String key2 = (victim.getCompany() + ";Buy" + ";Futures").toLowerCase();
			System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key1);
			HashMap<Integer, TradeInfo> futureTraderMap = future.get(key2);
			if (pastTraderMap != null && futureTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();

					if (futureTraderMap.containsKey(findInFuture)) {
						TradeInfo temp = (TradeInfo) pastTraderEntry.getValue();
						pastSecurities = temp.getQuantity();
						if (futureTraderMap.get(findInFuture).getQuantity() >= pastSecurities) {
							// fraud trade detected
//							this.fraudulentTransactions.add(findInFuture);
							System.out.println("Current Trade: "+ victim);
							TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
							System.out.println("Suspicious Trade/s: "+ tradeInfo.getTradeNumberList());
							System.out.println("Front running detected at: "+ futureTraderMap.get(findInFuture).getTradeNumberList());
							result.add(new ResultObject(victim, tradeInfo , futureTraderMap.get(findInFuture), 10 ));
						}

					}
				}
			}
		}
		
		//ssb fsf
		if (victim.getBuyOrSell().equals("Sell") && victim.getTypeOfSecurity().equals("Shares")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Sell" + ";Futures").toLowerCase();
			String key2 = (victim.getCompany() + ";Buy" + ";Futures").toLowerCase();
			//System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			HashMap<Integer, TradeInfo> futureTraderMap = future.get(key2);
			if (pastTraderMap != null && futureTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();

					if (futureTraderMap.containsKey(findInFuture)) {
						TradeInfo temp = (TradeInfo) pastTraderEntry.getValue();
						pastSecurities = temp.getQuantity();
						if (futureTraderMap.get(findInFuture).getQuantity() >= pastSecurities) {
							// fraud trade detected
//							this.fraudulentTransactions.add(findInFuture);
							System.out.println("Current Trade: "+ victim);
							TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
							System.out.println("Suspicious Trade/s: "+ tradeInfo.getTradeNumberList());
							System.out.println("Front running detected at: "+ futureTraderMap.get(findInFuture).getTradeNumberList());
							result.add(new ResultObject(victim, tradeInfo , futureTraderMap.get(findInFuture), 11 ));
						}

					}
				}
			}
		}
		
		//ssb sfs
		if (victim.getBuyOrSell().equals("Sell") && victim.getTypeOfSecurity().equals("Futures")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Sell" + ";Shares").toLowerCase();
			String key2 = (victim.getCompany() + ";Buy" + ";Shares").toLowerCase();
			System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			HashMap<Integer, TradeInfo> futureTraderMap = future.get(key2);
			if (pastTraderMap != null && futureTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();

					if (futureTraderMap.containsKey(findInFuture)) {
						TradeInfo temp = (TradeInfo) pastTraderEntry.getValue();
						pastSecurities = temp.getQuantity();
						if (futureTraderMap.get(findInFuture).getQuantity() >= pastSecurities) {
							// fraud trade detected
//							this.fraudulentTransactions.add(findInFuture);
							System.out.println("Current Trade: "+ victim);
							TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
							System.out.println("Suspicious Trade/s: "+ tradeInfo.getTradeNumberList());
							System.out.println("Front running detected at: "+ futureTraderMap.get(findInFuture).getTradeNumberList());
							result.add(new ResultObject(victim, tradeInfo , futureTraderMap.get(findInFuture), 12 ));
						}

					}
				}
			}
		}
			
		// bs sp
		if (victim.getBuyOrSell().equals("Sell") && victim.getTypeOfSecurity().equals("Shares")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Buy" + ";Put Option").toLowerCase();
			//System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			if (pastTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();
					TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
					System.out.println("Front Running Detected: "+ tradeInfo.getTradeNumberList());
					result.add(new ResultObject(victim, new TradeInfo(), tradeInfo, 13) );
				}
			}
			
		}
		
		// bs fp
		if (victim.getBuyOrSell().equals("Sell") && victim.getTypeOfSecurity().equals("Futures")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Buy" + ";Put Option").toLowerCase();
			//System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			if (pastTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();
					TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
					System.out.println("Front Running Detected: "+ tradeInfo.getTradeNumberList());
					result.add(new ResultObject(victim, new TradeInfo(), tradeInfo, 14) );
				}
			}
		}
		
		// ss sc
		if (victim.getBuyOrSell().equals("Sell") && victim.getTypeOfSecurity().equals("Shares")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Sell" + ";Call Option").toLowerCase();
			//System.out.println(key2);
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			if (pastTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();
					TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
					System.out.println("Front Running Detected: "+ tradeInfo.getTradeNumberList());
					result.add(new ResultObject(victim, new TradeInfo() , tradeInfo, 15) );
				}
			}
		}
		
		// ss fc
		if (victim.getBuyOrSell().equals("Sell") && victim.getTypeOfSecurity().equals("Futures")) {
			
			//System.out.println("Victim is buy");
			String key3 = (victim.getCompany() + ";Sell" + ";Call Option").toLowerCase();
			HashMap<Integer, TradeInfo> pastTraderMap = past.get(key3);
			if (pastTraderMap != null) {
				Integer findInFuture, pastSecurities;

				Set<Entry<Integer, TradeInfo>> pastMapIterSet = pastTraderMap.entrySet();

				for (Entry pastTraderEntry : pastMapIterSet) {
					findInFuture = (int) pastTraderEntry.getKey();
					TradeInfo tradeInfo = (TradeInfo)pastTraderEntry.getValue();
					System.out.println("Front Running Detected: "+ tradeInfo.getTradeNumberList());
					result.add(new ResultObject(victim, new TradeInfo(), tradeInfo, 16) );
				}
			}
			
		}

	}	
	
	public static void main(String[] args) {
		GenerateHashMap obj = new GenerateHashMap();
		JSONObject result = obj.parseDatabase(obj.trades, obj.past, obj.future);
		System.out.println(result);
	}
}


