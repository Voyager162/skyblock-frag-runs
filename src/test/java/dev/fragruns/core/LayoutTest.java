package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class LayoutTest {
    @ParameterizedTest @CsvSource({"320,240","640,360","854,480","960,540","1280,720","1920,1080","3440,1440"})
    void dashboardFitsEverySupportedViewport(int width,int height) {
        for(double scale:new double[]{0.5,1,1.5,2.5}) for(double anchor:new double[]{0,0.5,1}) {
            var b=new Layout(anchor,anchor,scale).fit(width,height,550,380);
            assertTrue(b.x()>=0&&b.y()>=0);assertTrue(b.x()+b.width()<=width);assertTrue(b.y()+b.height()<=height);
            assertEquals(275,b.localX(b.x()+b.width()/2),0.001);
        }
    }
    @Test void invalidSettingsCannotMakeUiDisappear() {
        var l=new Layout(Double.NaN,Double.POSITIVE_INFINITY,-9);var b=l.fit(854,480,550,380);
        assertTrue(Double.isFinite(b.x()));assertTrue(Double.isFinite(b.y()));assertTrue(b.scale()>0);
    }
    @Test void draggingOutsideScreenClampsAndRoundTripsCoordinates() {
        var l=new Layout();var b=l.fit(1000,800,550,380);l.move(-999,9999,1000,800,b);
        assertEquals(0,l.x);assertEquals(1,l.y);l.move(100,120,1000,800,b);var moved=l.fit(1000,800,550,380);assertEquals(100,moved.x(),0.001);assertEquals(120,moved.y(),0.001);
    }
}
