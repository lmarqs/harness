package thecook.checks

import org.junit.Test

class ModelTest {
  @Test void compositionAndReconciliationContract() {
    ModelChecks.main([System.getProperty('cook.report')] as String[])
  }
}
