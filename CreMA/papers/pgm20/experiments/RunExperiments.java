package pgm20.experiments;

import ch.idsia.crema.factor.bayesian.BayesianFactor;
import ch.idsia.crema.factor.credal.linear.IntervalFactor;
import ch.idsia.crema.factor.credal.vertex.VertexFactor;
import ch.idsia.crema.inference.causality.CausalInference;
import ch.idsia.crema.inference.causality.CausalVE;
import ch.idsia.crema.inference.causality.CredalCausalAproxLP;
import ch.idsia.crema.inference.causality.CredalCausalVE;
import ch.idsia.crema.model.graphical.specialized.StructuralCausalModel;
import ch.idsia.crema.models.causal.RandomChainMarkovian;
import ch.idsia.crema.models.causal.RandomChainNonMarkovian;
import ch.idsia.crema.utility.InvokerWithTimeout;
import ch.idsia.crema.utility.RandomUtil;
import gnu.trove.map.hash.TIntIntHashMap;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static ch.idsia.crema.models.causal.RandomChainNonMarkovian.buildModel;


public class RunExperiments {


    static StructuralCausalModel model;

    static TIntIntHashMap evidence, intervention;
    static int target;
    static double eps;

    static String method;

    static int warmups = 3;
    static int measures = 10;

    static boolean verbose = false;

    static long TIMEOUT = 5*60;


    public static void main(String[] args) throws InterruptedException {
        try {
            ////////// Input arguments Parameters //////////

            String modelName = "ChainMarkovian";

            /** Number of endogenous variables in the chain (should be 3 or greater)*/
            int N = 10;
            /** Number of states in the exogenous variables */
            int exoVarSize = 6;

            target = 1;

            int obsvar = N - 1;

            int dovar = 0;

            /** Inference method: CVE, CCVE, CCALP, CCALPeps  **/
            method = "CCALPeps";

            eps = 0.0;

            long seed = 1234;


            // ChainNonMarkovian 6 5 1 -1 0 CCALP 1234
            if (args.length > 0) {
                modelName = args[0];
                N = Integer.parseInt(args[1]);
                exoVarSize = Integer.parseInt(args[2]);
                target = Integer.parseInt(args[3]);
                obsvar = Integer.parseInt(args[4]);
                dovar = Integer.parseInt(args[5]);
                method = args[6];
                seed = Long.parseLong(args[7]);
            }

            if (method.equals("CCALPeps"))
                eps = 0.000001;

            System.out.println("\n" + modelName + "\n   N=" + N + " exovarsize=" + exoVarSize + " target=" + target + " obsvar=" + obsvar + " dovar=" + dovar + " method=" + method + " seed=" + seed);
            System.out.println("=================================================================");


            /////////////////////////////////
            RandomUtil.getRandom().setSeed(seed);


            /** Number of states in endogenous variables */
            int endoVarSize = 2;
            // Load the chain model

            if (modelName.equals("ChainMarkovian"))
                model = RandomChainMarkovian.buildModel(N, endoVarSize, exoVarSize);
            else if (modelName.equals("ChainNonMarkovian"))
                model = RandomChainNonMarkovian.buildModel(N, endoVarSize, exoVarSize);
            else
                throw new IllegalArgumentException("Non valid model name");


            int[] X = model.getEndogenousVars();

            evidence = new TIntIntHashMap();
            if (obsvar >= 0) evidence.put(obsvar, 0);

            intervention = new TIntIntHashMap();
            if (dovar >= 0) intervention.put(dovar, 0);

            System.out.println("Running experiments...");

            double res[] = run();
            System.out.println(res[0] + "," + res[1] + "," + res[2]);

        }catch (TimeoutException e){
            System.out.println(e);
            System.out.println("inf,inf,nan");
        }catch (Exception e){
            System.out.println(e);
            System.out.println("nan,nan,nan");
        }catch (Error e){
            System.out.println(e);
            System.out.println("nan,nan,nan");
        }


    }

    static double[] experiment() throws InterruptedException {
        Instant start = Instant.now();
        Instant queryStart = null;


        double intervalSize = 0.0;

        if(method.equals("CVE")) {
            CausalInference inf1 = new CausalVE(model);
            queryStart = Instant.now();
            BayesianFactor result1 = (BayesianFactor) inf1.query(target, evidence, intervention);
            if(verbose) System.out.println(result1);
        }else if(method.equals("CCVE")) {
            CausalInference inf2 = new CredalCausalVE(model);
            queryStart = Instant.now();
            VertexFactor result2 = (VertexFactor) inf2.query(target, evidence, intervention);
            if (verbose) System.out.println(result2);
            intervalSize = Stream.of(result2.filter(target,0).getData()[0]).mapToDouble(v->v[0]).max().getAsDouble() -
                    Stream.of(result2.filter(target,0).getData()[0]).mapToDouble(v->v[0]).min().getAsDouble();
        }else if (method.startsWith("CCALP")) {
            CausalInference inf3 = new CredalCausalAproxLP(model).setEpsilon(eps);
            queryStart = Instant.now();
            IntervalFactor result3 = (IntervalFactor) inf3.query(target, evidence, intervention);
            if(verbose) System.out.println(result3);
            intervalSize =  result3.getUpper(0)[0] - result3.getLower(0)[0];
        }else {
            throw new IllegalArgumentException("Unknown inference method");
        }

        Instant finish = Instant.now();
        double timeElapsed = Duration.between(start, finish).toNanos()/Math.pow(10,6);
        double timeElapsedQuery = Duration.between(queryStart, finish).toNanos()/Math.pow(10,6);

        return new double[]{timeElapsed, timeElapsedQuery, Math.abs(intervalSize)};
    }


    public static double[] run() throws InterruptedException, TimeoutException {

        double time[] = new double[measures];
        double time2[] = new double[measures];
        double size[] = new double[measures];

        ch.idsia.crema.utility.InvokerWithTimeout<double[]> invoker = new InvokerWithTimeout<>();

        // Warm-up
        for(int i=0; i<warmups; i++){
            double[] out = invoker.run(RunExperiments::experiment, TIMEOUT*2);
            System.out.println("Warm-up #"+i+" in "+out[0]+" ms.");
        }

        // Measures
        for(int i=0; i<measures; i++){

            double[] out = invoker.run(RunExperiments::experiment, TIMEOUT);
            System.out.println("Measurement #"+i+" in "+out[0]+" ms. size="+out[2]);
            time[i] = out[0];
            time2[i] = out[1];
            size[i] = out[2];
        }

        return new double[]{    DoubleStream.of(time).average().getAsDouble(),
                                DoubleStream.of(time2).average().getAsDouble(),
                                DoubleStream.of(size).average().getAsDouble()};
    }


}
