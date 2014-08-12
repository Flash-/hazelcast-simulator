package com.hazelcast.stabilizer.tests.map;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;
import com.hazelcast.core.IMap;
import com.hazelcast.core.TransactionalMap;
import com.hazelcast.stabilizer.tests.TestContext;
import com.hazelcast.stabilizer.tests.annotations.Run;
import com.hazelcast.stabilizer.tests.annotations.Setup;
import com.hazelcast.stabilizer.tests.annotations.Verify;
import com.hazelcast.stabilizer.tests.annotations.Warmup;
import com.hazelcast.stabilizer.tests.helpers.TxnCounter;
import com.hazelcast.stabilizer.tests.map.helpers.KeyInc;
import com.hazelcast.stabilizer.tests.utils.ThreadSpawner;
import com.hazelcast.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class MapTransactionContextConflictTest {

    public String basename = this.getClass().getName();
    public int threadCount = 3;
    public int keyCount = 50;
    public int maxKeysPerTxn =5;


    private HazelcastInstance targetInstance;
    private TestContext testContext;

    @Setup
    public void setup(TestContext testContext) throws Exception {
        this.testContext = testContext;
        targetInstance = testContext.getTargetInstance();
    }

    @Warmup(global = true)
    public void warmup() throws Exception {
        IMap<Integer, Double> map = targetInstance.getMap(basename);
        for (int k = 0; k < keyCount; k++) {
            map.put(k, 0.0);
        }
    }

    @Run
    public void run() {
        ThreadSpawner spawner = new ThreadSpawner(testContext.getTestId());
        for (int k = 0; k < threadCount; k++) {
            spawner.spawn(new Worker());
        }
        spawner.awaitCompletion();
    }

    private class Worker implements Runnable {
        private final Random random = new Random();
        private final double[] localIncrements = new double[keyCount];
        private TxnCounter count = new TxnCounter();


        @Override
        public void run() {
            while (!testContext.isStopped()) {

                List<KeyInc> taxParticipants = new ArrayList();

                for(int i=0; i< maxKeysPerTxn; i++){
                    KeyInc p = new KeyInc();
                    p.key = random.nextInt(keyCount);
                    p.inc = random.nextDouble();
                    taxParticipants.add(p);
                }

                TransactionContext context = targetInstance.newTransactionContext();
                try{
                    context.beginTransaction();

                    for(KeyInc p : taxParticipants){
                        final TransactionalMap<Integer, Double> map = context.getMap(basename);

                        double current = map.getForUpdate(p.key);
                        map.put(p.key, current + p.inc);

                        localIncrements[p.key]+=p.inc;
                    }
                    count.committed++;
                    context.commitTransaction();

                }catch(Exception commitFailed){
                    try{
                        context.rollbackTransaction();
                        count.rolled++;
                        count.committed--;
                        for(KeyInc p : taxParticipants){
                            localIncrements[p.key]-=p.inc;
                        }

                        System.out.println(basename+": commit   fail partisipents="+taxParticipants+" "+commitFailed);
                        commitFailed.printStackTrace();

                    }catch(Exception rollBackFailed){
                        count.failedRoles++;
                        System.out.println(basename+": rollback fail partisipents="+taxParticipants+" "+rollBackFailed);
                        rollBackFailed.printStackTrace();
                    }
                }
            }
            targetInstance.getList(basename+"res").add(localIncrements);
            targetInstance.getList(basename+"report").add(count);
        }
    }

    @Verify(global = false)
    public void verify() throws Exception {

        IList<TxnCounter> counts = targetInstance.getList(basename+"report");
        TxnCounter total = new TxnCounter();
        for(TxnCounter c : counts){
            total.add(c);
        }
        System.out.println(basename + ": "+total +" from "+counts.size()+" workers");

        IList<double[]> allIncrements = targetInstance.getList(basename+"res");
        double expected[] = new double[keyCount];
        for (double[] incs : allIncrements) {
            for (int i=0; i < incs.length; i++) {
                expected[i] += incs[i];
            }
        }

        IMap<Integer, Double> map = targetInstance.getMap(basename);

        int failures = 0;
        for (int k = 0; k < keyCount; k++) {
            if (expected[k] != map.get(k)) {
                failures++;

                System.out.println(basename+": key="+k+" expected "+expected[k]+" != " +"actual "+map.get(k));
            }
        }

        assertEquals(basename+": "+failures+" key=>values have been incremented unExpected", 0, failures);
    }

}

