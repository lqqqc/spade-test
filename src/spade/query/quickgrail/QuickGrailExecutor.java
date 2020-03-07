/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.query.quickgrail;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Kernel;
import spade.core.SPADEQuery;
import spade.core.SPADEQuery.QuickGrailInstruction;
import spade.query.quickgrail.core.GraphStats;
import spade.query.quickgrail.core.Program;
import spade.query.quickgrail.core.QueryEnvironment;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.Graph.Component;
import spade.query.quickgrail.instruction.CollapseEdge;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.CreateEmptyGraphMetadata;
import spade.query.quickgrail.instruction.DistinctifyGraph;
import spade.query.quickgrail.instruction.EraseSymbols;
import spade.query.quickgrail.instruction.EvaluateQuery;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.ExportGraph.Format;
import spade.query.quickgrail.instruction.GetAdjacentVertex;
import spade.query.quickgrail.instruction.GetEdge;
import spade.query.quickgrail.instruction.GetEdgeEndpoint;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLink;
import spade.query.quickgrail.instruction.GetPath;
import spade.query.quickgrail.instruction.GetShortestPath;
import spade.query.quickgrail.instruction.GetSubgraph;
import spade.query.quickgrail.instruction.GetVertex;
import spade.query.quickgrail.instruction.InsertLiteralEdge;
import spade.query.quickgrail.instruction.InsertLiteralVertex;
import spade.query.quickgrail.instruction.Instruction;
import spade.query.quickgrail.instruction.IntersectGraph;
import spade.query.quickgrail.instruction.LimitGraph;
import spade.query.quickgrail.instruction.ListGraphs;
import spade.query.quickgrail.instruction.OverwriteGraphMetadata;
import spade.query.quickgrail.instruction.PrintPredicate;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
import spade.query.quickgrail.parser.DSLParserWrapper;
import spade.query.quickgrail.parser.ParseProgram;
import spade.query.quickgrail.parser.ParseStatement;
import spade.query.quickgrail.types.LongType;
import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.QuickGrailPredicateTree.PredicateNode;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;
import spade.reporter.audit.OPMConstants;
import spade.resolver.RemoteLineageResolver;
import spade.utility.ABEGraph;
import spade.utility.DiscrepancyDetector;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

/**
 * Top level class for the QuickGrail graph query executor.
 */
public class QuickGrailExecutor{

	// TODO
	private static final DiscrepancyDetector discrepancyDetector = new DiscrepancyDetector();

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final static long exportGraphDumpLimit = 4096, exportGraphVisualizeLimit = 4096;

	private final QueryEnvironment queryEnvironment;
	private final QueryInstructionExecutor instructionExecutor;

	public QuickGrailExecutor(QueryInstructionExecutor instructionExecutor){
		this.instructionExecutor = instructionExecutor;
		if(this.instructionExecutor == null){
			throw new IllegalArgumentException("NULL instruction executor");
		}
		this.queryEnvironment = instructionExecutor.getQueryEnvironment();
		if(this.queryEnvironment == null){
			throw new IllegalArgumentException("NULL variable manager");
		}
	}

	public static void main(String[] args) throws Exception{
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

		System.out.print("-> ");

		String line = null;
		while((line = reader.readLine()) != null){
			if(line.trim().equalsIgnoreCase("quit") || line.trim().equalsIgnoreCase("exit")){
				break;
			}
			try{
				DSLParserWrapper parserWrapper = new DSLParserWrapper();
				ParseProgram parseProgram = parserWrapper.fromText(line);
				System.out.println(parseProgram);
//				for(ParseStatement statement : parseProgram.getStatements()){
//					System.out.println(statement);
//					ParseAssignment assign = (ParseAssignment)statement;
				// System.out.println(QuickGrailPredicateTree.resolveGraphPredicate(assign.getRhs()));
//					ParseOperation rhs = (ParseOperation)assign.getRhs();
//					System.out.println(rhs);

//					ArrayList<ParseExpression> operands = rhs.getOperands();
//					for(ParseExpression operand : operands){
//						System.out.println(operand);
//					}
//				}

				QuickGrailQueryResolver resolver = new QuickGrailQueryResolver();
				System.out.println();
				Program program = resolver.resolveProgram(parseProgram, null);
				System.out.println();
				System.out.println(program);
//				System.out.println(parseProgram);
			}catch(Exception e){
				e.printStackTrace();
			}
			System.out.println();
			System.out.print("-> ");
		}

		reader.close();
	}// java -jar ../../../../../lib/antlr-4.7-complete.jar DSL.g4

	public SPADEQuery execute(SPADEQuery query){
		try{
			
			DSLParserWrapper parserWrapper = new DSLParserWrapper();

			query.setQueryParsingStartedAtMillis();

			ParseProgram parseProgram = parserWrapper.fromText(query.query);

			final List<String> parsedProgramStatements = new ArrayList<String>();
			for(ParseStatement statement : parseProgram.getStatements()){
				parsedProgramStatements.add(String.valueOf(statement));
			}
			query.setQueryParsingCompletedAtMillis(parsedProgramStatements);

			logger.log(Level.INFO, "Parse tree:\n" + parseProgram.toString());

			query.setQueryInstructionResolutionStartedAtMillis();

			QuickGrailQueryResolver resolver = new QuickGrailQueryResolver();
			Program program = resolver.resolveProgram(parseProgram, queryEnvironment);

			query.setQueryInstructionResolutionCompletedAtMillis();

			logger.log(Level.INFO, "Execution plan:\n" + program.toString());

			for(int i = 0; i < program.getInstructionsSize(); i++){
				query.addQuickGrailInstruction(new QuickGrailInstruction(String.valueOf(program.getInstruction(i))));
			}

			try{
				query.setQueryExecutionStartedAtMillis();

				List<QuickGrailInstruction> queryInstructions = query.getQuickGrailInstructions();
				int instructionsSize = program.getInstructionsSize();
				for(int i = 0; i < instructionsSize; i++){
					Instruction executableInstruction = program.getInstruction(i);
					QuickGrailInstruction queryInstruction = queryInstructions.get(i);
					try{
						queryInstruction.setStartedAtMillis();

						query = executeInstruction(executableInstruction, query, queryInstruction);

						queryInstruction.setCompletedAtMillis();
					}catch(Exception e){
						queryInstruction.instructionFailed(new Exception("Instruction failed! " + e.getMessage(), e));
						throw e;
					}
				}

			}finally{
				queryEnvironment.gc();
				query.setQueryExecutionCompletedAtMillis();
			}

			// Only here if success
			if(query.getResult() != null){
				// The result of this query has already been pre-set by one of the
				// instructions.
				// This is done to always return the result of remote get lineage
				// since we cannot store it locally.
			}else{
				if(query.getQuickGrailInstructions().isEmpty()){
					query.querySucceeded("OK");
				}else{
					// Currently only return the last response.
					Serializable lastResult = query.getQuickGrailInstructions()
							.get(query.getQuickGrailInstructions().size() - 1).getResult();
					query.querySucceeded(lastResult);
				}
			}
			return query;
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);

			StringWriter stackTrace = new StringWriter();
			PrintWriter pw = new PrintWriter(stackTrace);
			pw.println("Error evaluating QuickGrail command:");
			pw.println("------------------------------------------------------------");
			e.printStackTrace(pw);
			pw.println(e.getMessage());
			pw.println("------------------------------------------------------------");

			query.queryFailed(new Exception(stackTrace.toString(), e));
			return query;
		}
	}

	// Have to set queryinstruction success
	private SPADEQuery executeInstruction(Instruction instruction, SPADEQuery query,
			QuickGrailInstruction queryInstruction) throws Exception{

		Serializable result = "OK"; // default result

		if(instruction.getClass().equals(ExportGraph.class)){
			result = exportGraph((ExportGraph)instruction);

		}else if(instruction.getClass().equals(CollapseEdge.class)){
			instructionExecutor.collapseEdge((CollapseEdge)instruction);

		}else if(instruction.getClass().equals(CreateEmptyGraph.class)){
			instructionExecutor.createEmptyGraph((CreateEmptyGraph)instruction);

		}else if(instruction.getClass().equals(CreateEmptyGraphMetadata.class)){
			instructionExecutor.createEmptyGraphMetadata((CreateEmptyGraphMetadata)instruction);

		}else if(instruction.getClass().equals(DistinctifyGraph.class)){
			instructionExecutor.distinctifyGraph((DistinctifyGraph)instruction);

		}else if(instruction.getClass().equals(EraseSymbols.class)){
			instructionExecutor.eraseSymbols((EraseSymbols)instruction);

		}else if(instruction.getClass().equals(EvaluateQuery.class)){
			ResultTable table = instructionExecutor.evaluateQuery((EvaluateQuery)instruction);
			if(table == null){
				result = "No Result!";
			}else{
				result = String.valueOf(table);
			}

		}else if(instruction.getClass().equals(GetAdjacentVertex.class)){
			instructionExecutor.getAdjacentVertex((GetAdjacentVertex)instruction);

		}else if(instruction.getClass().equals(GetEdge.class)){
			instructionExecutor.getEdge((GetEdge)instruction);

		}else if(instruction.getClass().equals(GetEdgeEndpoint.class)){
			instructionExecutor.getEdgeEndpoint((GetEdgeEndpoint)instruction);

		}else if(instruction.getClass().equals(GetLineage.class)){
			GetLineage getLineage = (GetLineage)instruction;
			if(getLineage.remoteResolve){
				getLineage(getLineage, query);
			}else{
				instructionExecutor.getLineage(getLineage);
			}
			
			// if here then it means that it was successful
			query.getQueryMetaData().setMaxLength(getLineage.depth);
			query.getQueryMetaData().setDirection(getLineage.direction);
			try{
				query.getQueryMetaData().addRootVertices(exportGraph(getLineage.targetGraph).vertexSet());
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to root vertices for transformers from get lineage query");
			}

		}else if(instruction.getClass().equals(GetLink.class)){
			instructionExecutor.getLink((GetLink)instruction);

		}else if(instruction.getClass().equals(GetPath.class)){
			instructionExecutor.getPath((GetPath)instruction);

		}else if(instruction.getClass().equals(GetShortestPath.class)){
			instructionExecutor.getShortestPath((GetShortestPath)instruction);

		}else if(instruction.getClass().equals(GetSubgraph.class)){
			instructionExecutor.getSubgraph((GetSubgraph)instruction);

		}else if(instruction.getClass().equals(GetVertex.class)){
			instructionExecutor.getVertex((GetVertex)instruction);

		}else if(instruction.getClass().equals(InsertLiteralEdge.class)){
			instructionExecutor.insertLiteralEdge((InsertLiteralEdge)instruction);

		}else if(instruction.getClass().equals(InsertLiteralVertex.class)){
			instructionExecutor.insertLiteralVertex((InsertLiteralVertex)instruction);

		}else if(instruction.getClass().equals(IntersectGraph.class)){
			instructionExecutor.intersectGraph((IntersectGraph)instruction);

		}else if(instruction.getClass().equals(LimitGraph.class)){
			instructionExecutor.limitGraph((LimitGraph)instruction);

		}else if(instruction.getClass().equals(ListGraphs.class)){
			ResultTable table = listGraphs((ListGraphs)instruction);
			if(table == null){
				result = "No Result!";
			}else{
				result = String.valueOf(table);
			}

		}else if(instruction.getClass().equals(OverwriteGraphMetadata.class)){
			instructionExecutor.overwriteGraphMetadata((OverwriteGraphMetadata)instruction);

		}else if(instruction.getClass().equals(SetGraphMetadata.class)){
			instructionExecutor.setGraphMetadata((SetGraphMetadata)instruction);

		}else if(instruction.getClass().equals(StatGraph.class)){
			GraphStats stats = instructionExecutor.statGraph((StatGraph)instruction);
			if(stats == null){
				result = "No Result!";
			}else{
				result = String.valueOf(stats);
			}

		}else if(instruction.getClass().equals(SubtractGraph.class)){
			instructionExecutor.subtractGraph((SubtractGraph)instruction);

		}else if(instruction.getClass().equals(UnionGraph.class)){
			instructionExecutor.unionGraph((UnionGraph)instruction);

		}else if(instruction.getClass().equals(PrintPredicate.class)){
			PredicateNode predicateNode = instructionExecutor.printPredicate((PrintPredicate)instruction);
			if(predicateNode == null){
				result = "No Result!";
			}else{
				result = String.valueOf(predicateNode);
			}

		}else{
			throw new RuntimeException("Unhandled instruction: " + instruction.getClass());
		}

		queryInstruction.instructionSucceeded(result);
		return query;
	}

	public spade.core.Graph exportGraph(ExportGraph instruction){// throws Exception{
		GraphStats stats = instructionExecutor.statGraph(new StatGraph(instruction.targetGraph));
		long verticesAndEdges = stats.vertices + stats.edges;
		if(!instruction.force){
			if(instruction.format == Format.kNormal && verticesAndEdges > exportGraphDumpLimit){
				throw new RuntimeException(
						"Dump export limit set at '" + exportGraphDumpLimit + "'. Total vertices and edges requested '"
								+ verticesAndEdges + "'." + "Please use 'force dump ...' to force the print.");
			}else if(instruction.format == Format.kDot && verticesAndEdges > exportGraphVisualizeLimit){
				throw new RuntimeException("Dot export limit set at '" + exportGraphVisualizeLimit
						+ "'. Total vertices and edges requested '" + verticesAndEdges + "'."
						+ "Please use 'force visualize ...' to force the transfer.");
			}
		}
		spade.core.Graph resultGraph = instructionExecutor.exportGraph(instruction);
		return resultGraph;
	}

	private ResultTable listGraphs(ListGraphs instruction){
		Map<String, GraphStats> graphsMap = instructionExecutor.listGraphs(instruction);

		List<String> sortedNonBaseSymbolNames = new ArrayList<String>();
		sortedNonBaseSymbolNames.addAll(graphsMap.keySet());
		sortedNonBaseSymbolNames.remove(queryEnvironment.getBaseSymbolName());
		Collections.sort(sortedNonBaseSymbolNames);

		ResultTable table = new ResultTable();
		for(String symbolName : sortedNonBaseSymbolNames){
			GraphStats graphStats = graphsMap.get(symbolName);
			ResultTable.Row row = new ResultTable.Row();
			row.add(symbolName);
			row.add(graphStats.vertices);
			row.add(graphStats.edges);
			table.addRow(row);
		}

		// Add base last
		GraphStats graphStats = graphsMap.get(queryEnvironment.getBaseSymbolName());
		ResultTable.Row row = new ResultTable.Row();
		row.add(queryEnvironment.getBaseSymbolName());
		row.add(graphStats.vertices);
		row.add(graphStats.edges);
		table.addRow(row);

		Schema schema = new Schema();
		schema.addColumn("Graph Name", StringType.GetInstance());
		if(!instruction.style.equals("name")){
			schema.addColumn("Number of Vertices", LongType.GetInstance());
			schema.addColumn("Number of Edges", LongType.GetInstance());
		}
		table.setSchema(schema);

		return table;
	}

	// TODO not a good location
	/*
	 * private void getPath(GetPath instruction){ Graph ancestorsOfFromGraph =
	 * queryEnvironment.allocateGraph(); instructionExecutor.createEmptyGraph(new
	 * CreateEmptyGraph(ancestorsOfFromGraph));
	 * 
	 * Graph descendantsOfToGraph = queryEnvironment.allocateGraph();
	 * instructionExecutor.createEmptyGraph(new
	 * CreateEmptyGraph(descendantsOfToGraph));
	 * 
	 * getLineage(new GetLineage(ancestorsOfFromGraph, instruction.subjectGraph,
	 * instruction.srcGraph, instruction.maxDepth, GetLineage.Direction.kAncestor,
	 * false));
	 * 
	 * getLineage(new GetLineage(descendantsOfToGraph, instruction.subjectGraph,
	 * instruction.dstGraph, instruction.maxDepth, GetLineage.Direction.kDescendant,
	 * false));
	 * 
	 * Graph intersectionGraph = queryEnvironment.allocateGraph();
	 * instructionExecutor.createEmptyGraph(new
	 * CreateEmptyGraph(intersectionGraph));
	 * 
	 * instructionExecutor .intersectGraph(new IntersectGraph(intersectionGraph,
	 * ancestorsOfFromGraph, descendantsOfToGraph));
	 * 
	 * Graph fromGraphInIntersection = queryEnvironment.allocateGraph();
	 * instructionExecutor.createEmptyGraph(new
	 * CreateEmptyGraph(fromGraphInIntersection));
	 * 
	 * instructionExecutor .intersectGraph(new
	 * IntersectGraph(fromGraphInIntersection, intersectionGraph,
	 * instruction.srcGraph));
	 * 
	 * Graph toGraphInIntersection = queryEnvironment.allocateGraph();
	 * instructionExecutor.createEmptyGraph(new
	 * CreateEmptyGraph(toGraphInIntersection));
	 * 
	 * instructionExecutor .intersectGraph(new IntersectGraph(toGraphInIntersection,
	 * intersectionGraph, instruction.dstGraph));
	 * 
	 * if(!instructionExecutor.statGraph(new
	 * StatGraph(fromGraphInIntersection)).isEmpty() &&
	 * !instructionExecutor.statGraph(new
	 * StatGraph(toGraphInIntersection)).isEmpty()){
	 * instructionExecutor.unionGraph(new UnionGraph(instruction.targetGraph,
	 * intersectionGraph)); // means we // found a path } }
	 */

	// Only called if remote resolve is true
	private void getLineage(final GetLineage instruction, final SPADEQuery originalSPADEQuery){
		GraphStats startGraphStats = getGraphStats(instruction.startGraph);
		if(startGraphStats.vertices == 0){
//			return empty TODO
		}

		if(startGraphStats.vertices > 1){
			throw new RuntimeException("Remote resolution of lineage of multiple vertices not supported yet");
		}

		spade.core.Graph startVertexGraph = exportGraph(instruction.startGraph);
		AbstractVertex startVertex = startVertexGraph.vertexSet().iterator().next(); // Only one allowed TODO

		Map<AbstractVertex, Integer> networkVertexToMinimumLevel = new HashMap<AbstractVertex, Integer>();

		int currentLevel = 1;

		Graph fromGraph = createNewGraph();
		instructionExecutor.distinctifyGraph(new DistinctifyGraph(fromGraph, instruction.startGraph)); // put into the
																										// variable
		unionGraph(instruction.targetGraph, instruction.startGraph);

		// TODO depth of every vertex for discrepancy detection needs to be set to
		// currentLevel+1. Don't know why.
		while(getGraphStats(fromGraph).vertices > 0){
			if(currentLevel > instruction.depth){
				break;
			}else{
				Graph thisLevelResultGraph = createNewGraph();
				instructionExecutor.getAdjacentVertex(new GetAdjacentVertex(thisLevelResultGraph,
						instruction.subjectGraph, fromGraph, instruction.direction));

				// order matters
				fromGraph = createNewGraph();
				instructionExecutor.subtractGraph(
						new SubtractGraph(fromGraph, thisLevelResultGraph, instruction.targetGraph, Component.kVertex));

				unionGraph(instruction.targetGraph, thisLevelResultGraph);

				GraphStats thisLevelResultGraphStats = getGraphStats(thisLevelResultGraph);
				if(thisLevelResultGraphStats.vertices == 0){
					break;
				}else{
					Set<AbstractVertex> thisLevelNetworkVertices = getNetworkVertices(thisLevelResultGraph);
					if(!thisLevelNetworkVertices.isEmpty()){
						for(AbstractVertex networkVertex : thisLevelNetworkVertices){
							if(networkVertex.isCompleteNetworkVertex() && // this is the 'abcdef' comment
									RemoteResolver.isRemoteAddressRemoteInNetworkVertex(networkVertex)
									&& !startVertex.equals(networkVertex)){
								if(networkVertexToMinimumLevel.get(networkVertex) == null){
									networkVertexToMinimumLevel.put(networkVertex, currentLevel);
								}else{
									// Useless at the moment
									int existingLevel = networkVertexToMinimumLevel.get(networkVertex);
									if(currentLevel < existingLevel){
										networkVertexToMinimumLevel.put(networkVertex, currentLevel);
									}
								}
							}
						}
					}
				}
				currentLevel++;
			}
		}

		Graph finalGraph = createNewGraph();
		instructionExecutor.distinctifyGraph(new DistinctifyGraph(finalGraph, instruction.targetGraph));

		startVertex.setDepth(0);

		spade.core.Graph localLineageGraph = exportGraph(finalGraph);
		localLineageGraph.setQueryString(originalSPADEQuery.query);
		localLineageGraph.setMaxDepth(instruction.depth);
		localLineageGraph.setHostName(Kernel.HOST_NAME);
		localLineageGraph.setRootVertex(startVertex);
		localLineageGraph.setComputeTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new Date()));

		for(Map.Entry<AbstractVertex, Integer> entry : networkVertexToMinimumLevel.entrySet()){
			localLineageGraph.putNetworkVertex(entry.getKey(), entry.getValue());
			entry.getKey().setDepth(entry.getValue());
		}

		// LOCAL query done by now

		Set<spade.core.Graph> subGraphs = new HashSet<spade.core.Graph>();
		List<SPADEQuery> subQueries = new ArrayList<SPADEQuery>();

		final boolean isDiscrepancyDetectionEnabled = isDiscrepancyDetectionEnabled();
		final boolean isRemoteResolutionRequired = networkVertexToMinimumLevel.size() > 0;// see 'abcdef' comment

		if(isRemoteResolutionRequired){
			String storageClassName = instructionExecutor.getStorageClass().getSimpleName();
			RemoteLineageResolver remoteLineageResolver = new RemoteLineageResolver(networkVertexToMinimumLevel,
					instruction.depth, instruction.direction, originalSPADEQuery.queryNonce, storageClassName);
			subQueries.addAll(remoteLineageResolver.resolve());
			subGraphs.addAll(getAllGraphs(subQueries));

			// Decryption code if required
//			ABE abe = new ABE();
//			if(abe.initialize(null)){
//				resultGraph = abe.decryptGraph((ABEGraph)resultGraph);
//			}else{
//				logger.log(Level.SEVERE, "Unable to decrypt the remote response graph");
//			}
			
			if(isDiscrepancyDetectionEnabled){
				doDiscrepancyDetection(subGraphs, instruction.direction); // some graphs might encrypted TODO
			}

			boolean areAllGraphsVerified = true;
			for(spade.core.Graph subGraph : subGraphs){
				areAllGraphsVerified = areAllGraphsVerified && subGraph.getIsResultVerified();
			}
			if(areAllGraphsVerified){
				// patching has to be done before adding the signature
				for(SPADEQuery subQuery : subQueries){
					if(subQuery != null && subQuery.getResult() != null
							&& subQuery.getResult().getClass().equals(spade.core.Graph.class)){
						AbstractVertex sourceNetworkVertex = ((spade.core.Graph)(subQuery.getResult())).getRootVertex(); // root might be encrypted TODO
						AbstractVertex targetNetworkVertex = RemoteResolver
								.findInverseNetworkVertex(networkVertexToMinimumLevel.keySet(), sourceNetworkVertex);
						AbstractEdge localToRemoteEdge = new Edge(sourceNetworkVertex, targetNetworkVertex);
						localToRemoteEdge.addAnnotation(OPMConstants.TYPE, OPMConstants.WAS_DERIVED_FROM);
						AbstractEdge remoteToLocalEdge = new Edge(targetNetworkVertex, sourceNetworkVertex);
						remoteToLocalEdge.addAnnotation(OPMConstants.TYPE, OPMConstants.WAS_DERIVED_FROM);
						localLineageGraph.putVertex(sourceNetworkVertex);
						localLineageGraph.putVertex(targetNetworkVertex);
						localLineageGraph.putEdge(localToRemoteEdge);
						localLineageGraph.putEdge(remoteToLocalEdge);
					}
				}
			}
		}

		localLineageGraph.addSignature(originalSPADEQuery.queryNonce);

		if(originalSPADEQuery.queryNonce == null){ // Going back to the original query sender
			// union all graphs and set
			for(spade.core.Graph subGraph : subGraphs){ // some graphs might be encrypted TODO
				localLineageGraph.union(subGraph);
			}
		}else{
			// Send back set of graphs
			for(SPADEQuery subQuery : subQueries){
				originalSPADEQuery.addRemoteSubquery(subQuery);
			}
		}

		originalSPADEQuery.querySucceeded(localLineageGraph);
	}

	//////////////////////////////////////////////////

	private GraphStats getGraphStats(Graph graph){
		return instructionExecutor.statGraph(new StatGraph(graph));
	}

	private spade.core.Graph exportGraph(Graph graph){
		return exportGraph(new ExportGraph(graph, Format.kDot, true));
	}

	private Graph createNewGraph(){
		Graph newGraph = instructionExecutor.getQueryEnvironment().allocateGraph();
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(newGraph));
		return newGraph;
	}

	private void unionGraph(Graph target, Graph source){
		instructionExecutor.unionGraph(new UnionGraph(target, source));
	}

	private Set<AbstractVertex> getNetworkVertices(Graph graph){
		Graph tempGraph = createNewGraph();
		instructionExecutor.getVertex(new GetVertex(tempGraph, graph, OPMConstants.ARTIFACT_SUBTYPE,
				PredicateOperator.EQUAL, OPMConstants.SUBTYPE_NETWORK_SOCKET));
		spade.core.Graph networkVerticesGraph = exportGraph(tempGraph);
		Set<AbstractVertex> networkVertices = new HashSet<AbstractVertex>();
		for(AbstractVertex networkVertex : networkVerticesGraph.vertexSet()){
			networkVertices.add(networkVertex);
		}
		return networkVertices;
	}

	//////////////////////////////////////////////////

	private boolean isDiscrepancyDetectionEnabled(){
		final String configFile = "find_inconsistency.txt";
		final String key = "find_inconsistency";
		try{
			return HelperFunctions
					.parseBoolean(FileUtility.readConfigFileAsKeyValueMap(configFile, "=").get(key)).result;
		}catch(Exception e){
			logger.log(Level.WARNING, "Not doing discrepancy detection. Failed to read key '" + key + "' config file '"
					+ configFile + "'", e);
			return false;
		}
	}

	private void doDiscrepancyDetection(Set<spade.core.Graph> graphs, final GetLineage.Direction direction){
		discrepancyDetector.setQueryDirection(direction.toString().toLowerCase().substring(1));
		discrepancyDetector.setResponseGraph(graphs);
		int discrepancyCount = discrepancyDetector.findDiscrepancy();
		logger.log(Level.INFO, "Discrepancy Count: " + discrepancyCount);
		if(discrepancyCount == 0){
			discrepancyDetector.update();
		}
	}

	private Set<spade.core.Graph> getAllGraphs(List<SPADEQuery> spadeQueries){
		Set<spade.core.Graph> graphs = new HashSet<spade.core.Graph>();

		for(SPADEQuery spadeQuery : spadeQueries){
			graphs.addAll(spadeQuery.getAllResultsOfExactType(spade.core.Graph.class));
			graphs.addAll(spadeQuery.getAllResultsOfExactType(ABEGraph.class));
		}

		return graphs;
	}
}