package test.fr.univ_nantes.ec_clem.registration;

import org.coinor.Ipopt;
import org.testng.annotations.Test;
import plugins.fr.univ_nantes.ec_clem.fixtures.fiducialset.TestFiducialSetFactory;
import javax.inject.Inject;
import static org.testng.Assert.assertEquals;

public class IpoptTest {

    private TestFiducialSetFactory testFiducialSetFactory;

    public IpoptTest() {
        DaggerIpoptTestComponent.create().inject(this);
    }

    @Inject
    public void setTestFiducialSetFactory(TestFiducialSetFactory testFiducialSetFactory) {
        this.testFiducialSetFactory = testFiducialSetFactory;
    }

    @Test
    void testExample() {
        fr.univ_nantes.ipopt.fixtures.HS071 hs071 = new fr.univ_nantes.ipopt.fixtures.HS071();
        int status = hs071.OptimizeNLP();
        assertEquals(Ipopt.SOLVE_SUCCEEDED, status);
    }
}
