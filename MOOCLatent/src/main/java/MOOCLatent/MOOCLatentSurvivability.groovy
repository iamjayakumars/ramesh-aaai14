package MOOCLatent

println "This source file is a place holder for the tree of groovy and java sources for your PSL project."

/*	PSL Model 1 for Student performance
 * 	Target : performance
 */


import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.collect.Iterables

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.umd.cs.bachuai13.util.DataOutputter;
import edu.umd.cs.bachuai13.util.ExperimentConfigGenerator;
import edu.umd.cs.bachuai13.util.FoldUtils;
import edu.umd.cs.bachuai13.util.GroundingWrapper;

import edu.umd.cs.psl.application.inference.LazyMPEInference;
import edu.umd.cs.psl.application.inference.MPEInference;
import edu.umd.cs.psl.application.learning.weight.em.HardEM;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.LazyMaxLikelihoodMPE;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxPseudoLikelihood
import edu.umd.cs.psl.application.learning.weight.maxmargin.MaxMargin
import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.database.ResultList
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.evaluation.result.*
import edu.umd.cs.psl.evaluation.statistics.RankingScore
import edu.umd.cs.psl.evaluation.statistics.SimpleRankingComparator
import edu.umd.cs.psl.groovy.PSLModel;
import edu.umd.cs.psl.groovy.PredicateConstraint;
import edu.umd.cs.psl.groovy.SetComparison;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.ui.functions.textsimilarity.*
import edu.umd.cs.psl.ui.loading.InserterUtils;
import edu.umd.cs.psl.util.database.Queries;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel
import edu.umd.cs.psl.model.parameters.Weight
import edu.umd.cs.psl.model.predicate.Predicate;

println "PSL Model for MOOC -- Baseline"

//config manager

ConfigManager cm = ConfigManager.getManager()
ConfigBundle config = cm.getBundle("mooc-survival-1")
Logger log = LoggerFactory.getLogger(this.class)
ExperimentConfigGenerator configGenerator = new ExperimentConfigGenerator("mooc-survival-1");
configGenerator.setLearningMethods(["MLE", "MPLE", "MM"]);

//database

def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = config.getString("dbpath", defaultPath + File.separator + "mooc-survival-1")
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config)

PSLModel m = new PSLModel(this, data)

timePeriod = 2;
//Aggregate predicates
m.add predicate: "postActivity" , types: [ArgumentType.UniqueID]
m.add predicate: "viewActivity" , types: [ArgumentType.UniqueID]
m.add predicate: "voteActivity" , types: [ArgumentType.UniqueID]
m.add predicate: "reputation" , types: [ArgumentType.UniqueID]
m.add predicate: "ontime" , types: [ArgumentType.UniqueID]
m.add predicate: "submittedLecture" , types: [ArgumentType.UniqueID]

//Post-level predicates

m.add predicate: "posts" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "votes" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "views" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "upvote" , types: [ArgumentType.UniqueID]
m.add predicate: "downvote" , types: [ArgumentType.UniqueID]
m.add predicate: "subscribeForums" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "subscribeThreads" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
//m.add predicate: "reply" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

//Type predicates
m.add predicate: "post" , types: [ArgumentType.UniqueID]
m.add predicate: "user" , types: [ArgumentType.UniqueID]

m.add predicate: "polarity" , types: [ArgumentType.UniqueID]
m.add predicate: "subjective" , types: [ArgumentType.UniqueID]
//m.add predicate: "negative" , types: [ArgumentType.UniqueID]
m.add predicate: "inThread" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "inForum" , types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

//Target predicate
m.add predicate: "performance" , types: [ArgumentType.UniqueID]

//Engagement predicates
m.add predicate: "engagement_active" , types: [ArgumentType.UniqueID]
m.add predicate: "engagement_passive" , types: [ArgumentType.UniqueID]
m.add predicate: "disengagement" , types: [ArgumentType.UniqueID]
m.add predicate: "engagement" , types: [ArgumentType.UniqueID]
//Forum topic type
m.add predicate: "forumType" , types: [ArgumentType.UniqueID, ArgumentType.String]

//temporal predicates
m.add predicate: "lastPost" , types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "lastQuiz" , types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "lastLecture" , types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "lastView" , types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "lastVote" , types: [ArgumentType.UniqueID, ArgumentType.String]

//rules
//post activity indicates performance
m.add rule : ( postActivity(U) ) >> engagement_active(U),  weight : 5
m.add rule : ( user(U) & ~postActivity(U) ) >> ~engagement_active(U),  weight : 5
//vote activity indicates performance

m.add rule : ( voteActivity(U) ) >> engagement_passive(U),  weight : 5
m.add rule : ( user(U) & ~voteActivity(U) ) >> ~engagement_passive(U),  weight : 5

//vote activity and post activity indicates performance

m.add rule : ( viewActivity(U) ) >> engagement_passive(U),  weight : 5
m.add rule : ( user(U) & ~viewActivity(U) ) >> ~engagement_passive(U),  weight : 5

//all activity indicates performance
//m.add rule : ( postActivity(U) & viewActivity(U) & voteActivity(U) ) >> performance(U),  weight : 5

//reputation indicates performance
m.add rule : ( engagement_active(U) & reputation(U) ) >> performance(U),  weight : 5
m.add rule : ( engagement_active(U) & user(U) & ~reputation(U) ) >> ~performance(U),  weight : 5

//posts positive stuff
m.add rule : ( posts(U, P) ) >> engagement_active(U),  weight : 5
m.add rule : ( posts(U, P) & polarity(P)) >> engagement_active(U),  weight : 5
m.add rule : ( posts(U, P) & subjective(P)) >> engagement_active(U),  weight : 5
m.add rule : ( posts(U, P) & post(P) & ~polarity(P)) >> ~engagement_active(U),  weight : 5

//posts stuff that has positive votes
m.add rule : ( posts(U, P) & upvote(P)) >> engagement_active(U),  weight : 5
m.add rule : ( posts(U, P) & post(P) & ~upvote(P)) >> ~engagement_active(U),  weight : 5

//posts stuff that has negative votes
m.add rule : ( posts(U, P) & downvote(P) ) >> engagement_active(U),  weight : 3

//temporal rules
m.add rule : ( lastLecture(U, timePeriod.toString()) ) >> disengagement(U),  weight : 10

m.add rule : ( lastQuiz(U, timePeriod.toString()) ) >> disengagement(U),  weight : 10

m.add rule : ( lastPost(U, timePeriod.toString()) ) >> disengagement(U),  weight : 10

m.add rule : ( lastLecture(U, (timePeriod-1).toString()) ) >> disengagement(U),  weight : 10

m.add rule : ( lastQuiz(U, (timePeriod-1).toString()) ) >> disengagement(U),  weight : 10

m.add rule : ( lastPost(U, (timePeriod-1).toString()) ) >> disengagement(U),  weight : 10

m.add rule : ( lastLecture(U, (timePeriod+1).toString()) ) >> engagement_passive(U),  weight : 10

m.add rule : ( lastQuiz(U, (timePeriod+1).toString()) ) >> engagement_active(U),  weight : 10

m.add rule : ( lastPost(U, (timePeriod+1).toString()) ) >> engagement_active(U),  weight : 10

m.add rule : ( lastPost(U, timePeriod.toString()) & lastQuiz(U, timePeriod.toString()) ) >> disengagement(U),  weight : 10
m.add rule : ( lastPost(U, (timePeriod-1).toString()) & lastQuiz(U, (timePeriod-1).toString()) ) >> disengagement(U),  weight : 10



//ontime with the course
m.add rule : ( engagement_passive(U) ) >> performance(U),  weight : 5
m.add rule : ( engagement_active(U) ) >> performance(U),  weight : 5
m.add rule : ( engagement_active(U) & engagement_passive(U)) >> performance(U),  weight : 5
m.add rule : ( user(U) & ~engagement_active(U) & ~engagement_passive(U)) >> disengagement(U),  weight : 5
m.add rule : ( user(U) & ~engagement_active(U) & ~engagement_passive(U)) >> ~performance(U),  weight : 5
m.add rule : ( user(U) & ~engagement_active(U) & engagement_passive(U)) >> ~performance(U),  weight : 5
m.add rule : ( user(U) & ~engagement_active(U) ) >> ~performance(U), weight : 10
m.add rule : ( user(U) & disengagement(U) ) >> ~performance(U), weight : 10

m.add rule : ( user(U) & ~submittedLecture(U) ) >> ~engagement_passive(U),  weight : 10

m.add rule : ( submittedLecture(U) ) >> engagement_passive(U),  weight : 5
m.add rule : ( subscribeForums(U, F) ) >> engagement_passive(U),  weight : 5
m.add rule : ( subscribeThreads(U, T) ) >> engagement_passive(U),  weight : 5
m.add rule : ( subscribeThreads(U, T) & subscribeForums(U, F)) >> engagement_passive(U),  weight : 7
//m.add rule : ( engagement_active(U) & submitted(U) ) >> performance(U),  weight : 5
//m.add rule : ( engagement_active(U) & ontime(U) ) >> engagement_active(U),  weight : 5

//network rules

m.add rule : ( posts(U1, P1) & posts(U2, P2) & performance(U1) & inThread(U1, T) & inThread(U2, T) ) >> performance(U2),  weight : 5
m.add rule : ( posts(U1, P1) & posts(U2, P2) & engagement_active(U1) & inThread(U1, T) & inThread(U2, T) ) >> engagement_active(U2),  weight : 5


Map<CompatibilityKernel,Weight> weights = new HashMap<CompatibilityKernel, Weight>()
for (CompatibilityKernel k : Iterables.filter(m.getKernels(), CompatibilityKernel.class))
	weights.put(k, k.getWeight());

//Read data in database
def trainDir = 'data'+java.io.File.separator+'gene'+java.io.File.separator+'start-end'+java.io.File.separator
Partition trainPart = new Partition(0)
Partition targetPart = new Partition(1)
for (Predicate p : [postActivity, reputation, submittedlecture, viewActivity, voteActivity, polarity, subjective])
{
	println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, trainPart)
	println trainDir+p.getName()+".txt"
	InserterUtils.loadDelimitedDataTruth(insert, trainDir+p.getName().toLowerCase()+".txt");
}
for (Predicate p : [user, post, upvote, downvote, posts, inForum, lastLecture, lastPost, lastQuiz, lastView, subscribeForums, subscribeThreads])
{
	println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, trainPart)
	println trainDir+p.getName().toString()+".txt"
	InserterUtils.loadDelimitedData(insert, trainDir+p.getName().toString().toLowerCase()+".txt");
}
for (Predicate p : [performance])
{
	println "\t\t\tREADING " + p.getName() +" ...";
	insert = data.getInserter(p, targetPart)
	println trainDir+p.getName().toString()+".txt"
	InserterUtils.loadDelimitedDataTruth(insert, trainDir+p.getName().toString().toLowerCase()+".txt");
}


folds = 10

List<Partition> targetPartitions = new ArrayList<Partition>(folds)
List<Partition> trainWritePartitions = new ArrayList<Partition>(folds)
List<Partition> testWritePartitions = new ArrayList<Partition>(folds)

for (int i=0; i< folds; i++) {
	targetPartitions.add(i, new Partition(i + 2))
	trainWritePartitions.add(i, new Partition(i + 1*folds + 2))
	testWritePartitions.add(i, new Partition(i + 2*folds + 2))
}

List<Set<GroundingWrapper>> groundings = FoldUtils.splitGroundings(data, [performance], [targetPart], folds)

for (int i = 0; i < folds; i++) {
	FoldUtils.copy(data, targetPart, targetPartitions.get(i), performance, groundings.get(i))
}

List<List<Double []>> results = new ArrayList<List<Double []>>()
results.add(new ArrayList<Double []>())
	
for (int fold = 0; fold < folds; fold++) {
	
	/*
	 *  Populate train and test partitions
	 *  TRAIN
	 *  Observed : All
	 *  Target : All except "fold"
	 *  TEST:
	 *  Observed: All
	 *  Target: Only "fold" unobserved, rest observed, predict performance of "fold"
	 */
	ArrayList<Partition> trainReadPartitions = new ArrayList<Partition>();
	ArrayList<Partition> testReadPartitions = new ArrayList<Partition>();
	int trainingTarget = (fold - 1) % folds
	if (trainingTarget < 0) trainingTarget += folds
	Partition trainLabelPartition = targetPartitions.get(trainingTarget)
	for (int i = 0; i < folds; i++) {
		if (i != fold) {
			testReadPartitions.add(targetPartitions.get(i))
			trainReadPartitions.add(targetPartitions.get(i))
		}
//
//		if (i != trainingTarget && i != fold)
			
	}
	testReadPartitions.add(trainPart)
	//trainReadPartitions.add(trainPart)
	
	Partition testLabelPartition = targetPartitions.get(fold)
	
	Partition dummy = new Partition(200)
	Partition dummy2 = new Partition(201)
	Database trainDB = data.getDatabase(trainWritePartitions.get(fold),[postActivity, ontime, reputation, polarity, subjective, submittedLecture, user, post, upvote, downvote, posts, viewActivity, voteActivity, inThread, inForum, lastQuiz, lastPost, lastLecture, lastView] as Set, (Partition []) trainPart)
	Database testDB = data.getDatabase(testWritePartitions.get(fold), [postActivity, ontime, reputation, polarity, subjective, submittedLecture, user, post, upvote, downvote, posts, viewActivity, voteActivity, inThread, inForum, lastQuiz, lastPost, lastLecture, lastView] as Set, (Partition []) testReadPartitions.toArray())
	Database labelsDB = data.getDatabase(dummy, [performance] as Set, (Partition []) trainReadPartitions.toArray())//targetPartitions.get(trainingTarget))
	
	
	DataOutputter.outputPredicate("output/training-truth" + fold + ".val" , labelsDB, performance, ",", true, "User,Truth");
	
	/*
	 * POPULATE TRAINING DATABASE
	 * Get all knows pairs,
	 */
	int rv = 0, ob = 0
	ResultList allGroundings = trainDB.executeQuery(Queries.getQueryForAllAtoms(user))
	for (Predicate p : [engagement_active, engagement_passive, disengagement, performance]) {
		for (int i = 0; i < allGroundings.size(); i++) {
			GroundTerm [] grounding = allGroundings.get(i)
			GroundAtom atom = trainDB.getAtom(p, grounding)
			if (atom instanceof RandomVariableAtom) {
				rv++
				trainDB.commit((RandomVariableAtom) atom);
			}
			else
				ob++
		}
	}
	System.out.println("Saw " + rv + " rvs and " + ob + " obs")
	/*
	 * POPULATE TEST DATABASE
	 */
	allGroundings = testDB.executeQuery(Queries.getQueryForAllAtoms(user))
	for (Predicate p : [engagement_active, engagement_passive, disengagement, performance]) {
		for (int i = 0; i < allGroundings.size(); i++) {
			GroundTerm [] grounding = allGroundings.get(i)
			GroundAtom atom = testDB.getAtom(p, grounding)
			if (atom instanceof RandomVariableAtom) {
				testDB.commit((RandomVariableAtom) atom);
			}
		}
	}
	for (CompatibilityKernel k : Iterables.filter(m.getKernels(), CompatibilityKernel.class))
		k.setWeight(weights.get(k))
	//weight learning
	HardEM weightLearning = new HardEM(m, trainDB, labelsDB, config);

	weightLearning.learn();
	weightLearning.close();
	
	System.out.println("Learned model " + config.getString("name", "") + "\n" + m.toString())
	
	MPEInference mpe = new MPEInference(m, testDB, config)
	FullInferenceResult result = mpe.mpeInference()
	/*
	 * Inferred Values Print
	 */
//	for (GroundAtom atom : Queries.getAllAtoms(testDB, disengagement)){
//		println atom.toString() + "\t" + atom.getValue();
//	}
//	for (GroundAtom atom : Queries.getAllAtoms(testDB, engagement_active)){
//		println atom.toString() + "\t" + atom.getValue();
//	}
	ArrayList<String> testUsers = new ArrayList<>();
	for (GroundAtom atom : Queries.getAllAtoms(testDB, performance)){
		if (atom instanceof RandomVariableAtom) {
			println atom.getArguments()[0].toString() + "\t" + atom.getValue();
			testUsers.add(atom.getArguments()[0].toString());
		}
	}
	
//	DataOutputter.outputPredicateOnlyTest("data/t"+(timePeriod-1)+"/output/results-performance" + fold + ".val" , testDB, performance, ",", true, "User,Truth", testUsers);
//	DataOutputter.outputPredicateOnlyTest("data/t"+(timePeriod-1)+"/output/results-engagement-active" + fold + ".val" , testDB, engagement_active, ",", true, "User,Truth", testUsers);
//	DataOutputter.outputPredicateOnlyTest("data/t"+(timePeriod-1)+"/output/results-engagement-passive" + fold + ".val" , testDB, engagement_passive, ",", true, "User,Truth", testUsers);
//	DataOutputter.outputPredicateOnlyTest("data/t"+(timePeriod-1)+"/output/results-disengagement" + fold + ".val" , testDB, disengagement, ",", true, "User,Truth", testUsers);
	testDB.close()
	Database resultsDB = data.getDatabase(dummy2, testWritePartitions.get(fold))
	def comparator = new SimpleRankingComparator(resultsDB)
	def groundTruthDB = data.getDatabase(testLabelPartition, [performance] as Set)
	comparator.setBaseline(groundTruthDB)
	
	/*
	 * Ground truth DB sanity check
	 */
//	println "ground truth db"
//	for (GroundAtom atom : Queries.getAllAtoms(groundTruthDB, performance)){
//		println atom.toString() + "\t" + atom.getValue();
//	}
	def metrics = [RankingScore.AUPRC, RankingScore.NegAUPRC, RankingScore.AreaROC, RankingScore.Kendall]
	double [] score = new double[metrics.size()]

	for (int i = 0; i < metrics.size(); i++) {
		comparator.setRankingScore(metrics.get(i))
		score[i] = comparator.compare(performance)
	}
	System.out.println("Area under positive-class PR curve: " + score[0])
	System.out.println("Area under negative-class PR curve: " + score[1])
	System.out.println("Area under ROC curve: " + score[2])
	System.out.println("Kendall's: " + score[3])
//	println "train DB"
//	for (GroundAtom atom : Queries.getAllAtoms(trainDB, performance)){
//		println atom.toString() + "\t" + atom.getValue();
//	}
	results.get(0).add(fold, score)
	resultsDB.close()
	groundTruthDB.close()
	trainDB.close();
	//testDB.close();
	labelsDB.close()
	
}

def methodStats = results.get(0)
sum = new double[4];
sumSq = new double[4];
for (int fold = 0; fold < folds; fold++) {
	def score = methodStats.get(fold)
	for (int i = 0; i < 4; i++) {
		sum[i] += score[i];
		sumSq[i] += score[i] * score[i];
	}
	System.out.println("Method " + config + ", fold " + fold +", auprc positive: "
			+ score[0] + ", negative: " + score[1] + ", auROC: " + score[2] + ", kendall: " + score[3])
}

mean = new double[4];
variance = new double[4];
for (int i = 0; i < 4; i++) {
	mean[i] = sum[i] / folds;
	variance[i] = sumSq[i] / folds - mean[i] * mean[i];
}

System.out.println();
System.out.println("Method " + config + ", auprc positive: (mean/variance) "
		+ mean[0] + "  /  " + variance[0] );
System.out.println("Method " + config + ", auprc negative: (mean/variance) "
		+ mean[1] + "  /  " + variance[1] );
System.out.println("Method " + config + ", auROC: (mean/variance) "
		+ mean[2] + "  /  " + variance[2] );
System.out.println("Method " + config + ", kendall: (mean/variance) "
		+ mean[3] + "  /  " + variance[3] );
System.out.println();
