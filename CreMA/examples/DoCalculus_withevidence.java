import ch.idsia.crema.factor.bayesian.BayesianFactor;
import ch.idsia.crema.factor.credal.linear.IntervalFactor;
import ch.idsia.crema.factor.credal.vertex.VertexFactor;
import ch.idsia.crema.inference.approxlp2.ApproxLP2;
import ch.idsia.crema.inference.ve.FactorVariableElimination;
import ch.idsia.crema.inference.ve.VariableElimination;
import ch.idsia.crema.model.graphical.SparseDirectedAcyclicGraph;
import ch.idsia.crema.model.graphical.SparseModel;
import ch.idsia.crema.model.graphical.specialized.StructuralCausalModel;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.util.Arrays;
import java.util.stream.IntStream;

public class DoCalculus_withevidence {
    public static void main(String[] args) throws InterruptedException {


        // Marcovian case with 4 endogenous variables
        // x <- z -> y ;  x -> y <- w

        int x=0, y=1, z=2, w=3;
        int[] endoVarSizes = {2,2,2,2};
        SparseDirectedAcyclicGraph dag = new SparseDirectedAcyclicGraph();
        dag.addVariable(x);
        dag.addVariable(y);
        dag.addVariable(z);
        dag.addVariable(w);
        dag.addLink(x,y);
        dag.addLink(z,x);
        dag.addLink(z,y);
        dag.addLink(w,y);

        // Build the causal model
        StructuralCausalModel smodel = new StructuralCausalModel(dag, endoVarSizes);
        smodel.fillWithRandomFactors(2);


        //////// Inputs /////

        StructuralCausalModel model = smodel;

        TIntIntMap evidence = new TIntIntHashMap();
        evidence.put(w, 0);

        TIntIntMap intervention = new TIntIntHashMap();
        intervention.put(x, 0);

        int[] target = {y};

        //////////////////


        // Case 0: operating with the factors

        BayesianFactor result_0 = model.getProb(y).filter(x,0).filter(w,0).combine(model.getProb(z)).marginalize(z);
        System.out.println(Arrays.toString(result_0.getData()));


        // Case 1: CausalVariableElimination

        StructuralCausalModel do_model = model.intervention(intervention.keys()[0], intervention.values()[0]);

        VariableElimination ve = new FactorVariableElimination(do_model.getVariables());
        ve.setEvidence(evidence);
        ve.setFactors(do_model.getFactors());
        BayesianFactor result_1 = (BayesianFactor) ve.run(target);

        System.out.println(Arrays.toString(result_1.getData()));


        // Case 2: CredalCausalVariableElimination

        BayesianFactor[] factors  = IntStream.of(model.getEndogenousVars())
                .mapToObj(v -> model.getProb(v).fixPrecission(5,v))
                .toArray(BayesianFactor[]::new);


        SparseModel csmodel = smodel.toCredalNetwork(true, factors);
        SparseModel do_csmodel = csmodel.intervention(intervention.keys()[0], intervention.values()[0]);


        ve = new FactorVariableElimination(do_csmodel.getVariables());
        ve.setFactors(do_csmodel.getFactors());
        ve.setEvidence(evidence);
        ve.setNormalize(false);
        VertexFactor result_2 = ((VertexFactor) ve.run(target)).normalize();

        System.out.println(result_2);


        // Case 3: CredalCausalApproxLP

        csmodel = smodel.toCredalNetwork(false, factors);
        do_csmodel = csmodel.intervention(intervention.keys()[0], intervention.values()[0])
        // .intervention(evidence.keys()[0], evidence.values()[0])

        ApproxLP2 lp = new ApproxLP2();
        IntervalFactor result_3 = lp.query(do_csmodel, target[0], evidence);    // NOT WORKING !!
        System.out.println(Arrays.toString(result_3.getUpper()));
        System.out.println(Arrays.toString(result_3.getLower()));




    }
}
