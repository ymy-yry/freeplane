package org.freeplane.plugin.ai.tools.utilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ObservableToolExecutorTest {

    @Test
    public void executeWithContext_notifiesObserversInCorrectOrder() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        ToolExecutionResult expectedResult = ToolExecutionResult.builder()
            .resultText("success")
            .isError(false)
            .build();
        org.mockito.Mockito.when(delegate.executeWithContext(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenReturn(expectedResult);
        
        TestObserver observer1 = new TestObserver();
        TestObserver observer2 = new TestObserver();
        List<ToolExecutionObserver> observers = Arrays.asList(observer1, observer2);
        
        ObservableToolExecutor executor = new ObservableToolExecutor(
            delegate, "testTool", ToolCaller.MCP, observers);
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name("testTool")
            .arguments("{\"key\": \"value\"}")
            .build();
        
        ToolExecutionResult result = executor.executeWithContext(request, InvocationContext.builder().build());
        
        // Verify delegate was called
        verify(delegate).executeWithContext(ArgumentMatchers.any(), ArgumentMatchers.any());
        
        // Verify both observers were notified
        assertThat(observer1.beforeCount).isEqualTo(1);
        assertThat(observer1.afterCount).isEqualTo(1);
        assertThat(observer1.errorCount).isEqualTo(0);
        
        assertThat(observer2.beforeCount).isEqualTo(1);
        assertThat(observer2.afterCount).isEqualTo(1);
        assertThat(observer2.errorCount).isEqualTo(0);
        
        // Verify result
        assertThat(result.resultText()).isEqualTo("success");
        assertThat(result.isError()).isFalse();
    }

    @Test
    public void executeWithContext_onBeforeException_abortsExecution() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        
        TestObserver observer1 = new TestObserver();
        observer1.throwOnBefore = true;
        
        List<ToolExecutionObserver> observers = Collections.singletonList(observer1);
        
        ObservableToolExecutor executor = new ObservableToolExecutor(
            delegate, "testTool", ToolCaller.MCP, observers);
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name("testTool")
            .arguments("{}")
            .build();
        
        try {
            executor.executeWithContext(request, InvocationContext.builder().build());
        } catch (RuntimeException e) {
            // Expected - onBefore exception should propagate
            assertThat(e.getMessage()).isEqualTo("onBefore test exception");
        }
        
        // Verify delegate was NOT called (execution aborted)
        verify(delegate, never()).executeWithContext(ArgumentMatchers.any(), ArgumentMatchers.any());
        
        // Verify onBefore was called but onAfter was not
        assertThat(observer1.beforeCount).isEqualTo(1);
        assertThat(observer1.afterCount).isEqualTo(0);
    }

    @Test
    public void executeWithContext_onAfterException_doesNotDisruptExecution() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        ToolExecutionResult expectedResult = ToolExecutionResult.builder()
            .resultText("success")
            .isError(false)
            .build();
        org.mockito.Mockito.when(delegate.executeWithContext(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenReturn(expectedResult);
        
        TestObserver observer1 = new TestObserver();
        observer1.throwOnAfter = true;
        
        List<ToolExecutionObserver> observers = Collections.singletonList(observer1);
        
        ObservableToolExecutor executor = new ObservableToolExecutor(
            delegate, "testTool", ToolCaller.MCP, observers);
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name("testTool")
            .arguments("{}")
            .build();
        
        // Should not throw - onAfter exceptions are swallowed
        ToolExecutionResult result = executor.executeWithContext(request, InvocationContext.builder().build());
        
        // Verify result is still returned
        assertThat(result.resultText()).isEqualTo("success");
        
        // Verify onAfter was called
        assertThat(observer1.afterCount).isEqualTo(1);
    }

    @Test
    public void executeWithContext_onErrorException_notifiesErrorObserver() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        RuntimeException expectedException = new RuntimeException("Tool execution failed");
        org.mockito.Mockito.when(delegate.executeWithContext(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenThrow(expectedException);
        
        TestObserver observer1 = new TestObserver();
        List<ToolExecutionObserver> observers = Collections.singletonList(observer1);
        
        ObservableToolExecutor executor = new ObservableToolExecutor(
            delegate, "testTool", ToolCaller.MCP, observers);
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name("testTool")
            .arguments("{}")
            .build();
        
        try {
            executor.executeWithContext(request, InvocationContext.builder().build());
        } catch (RuntimeException e) {
            assertThat(e).isEqualTo(expectedException);
        }
        
        // Verify error notification
        assertThat(observer1.beforeCount).isEqualTo(1);
        assertThat(observer1.afterCount).isEqualTo(0);
        assertThat(observer1.errorCount).isEqualTo(1);
    }

    /**
     * Test observer that counts event notifications
     */
    private static class TestObserver implements ToolExecutionObserver {
        int beforeCount = 0;
        int afterCount = 0;
        int errorCount = 0;
        boolean throwOnBefore = false;
        boolean throwOnAfter = false;

        @Override
        public void onBefore(ToolExecutionBeforeEvent event) {
            beforeCount++;
            if (throwOnBefore) {
                throw new RuntimeException("onBefore test exception");
            }
        }

        @Override
        public void onAfter(ToolExecutionAfterEvent event) {
            afterCount++;
            if (throwOnAfter) {
                throw new RuntimeException("onAfter test exception");
            }
        }

        @Override
        public void onError(ToolExecutionErrorEvent event) {
            errorCount++;
        }
    }
}
