/*
 * Copyright 2015 CIRDLES.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.earthtime.UPb_Redux.aliquots;

import java.math.BigDecimal;
import org.earthtime.UPb_Redux.fractions.Fraction;
import org.earthtime.UPb_Redux.fractions.UPbReduxFractions.UPbFraction;
import org.earthtime.UPb_Redux.reduxLabData.ReduxLabData;
import org.earthtime.UPb_Redux.samples.SESARSampleMetadata;
import org.earthtime.UPb_Redux.valueModels.SampleDateModel;
import org.earthtime.ratioDataModels.physicalConstantsModels.PhysicalConstantsModel;
import org.junit.Test;

/**
 *
 * @author parizotclement
 */
public class UPbReduxAliquotTest {
    
    @Test
    public void testXMLSerialization() throws Exception {
        Aliquot aliquot
                = new UPbReduxAliquot(
                        0,
                        "Test Aliquot",
                        ReduxLabData.getInstance(),
                        PhysicalConstantsModel.getEARTHTIMEPhysicalConstantsModel(),
                        false,
                        new SESARSampleMetadata());

        Fraction uPbfraction = new UPbFraction("NONE");
        uPbfraction.setSampleName("TestSample");
        uPbfraction.setFractionID("TestFraction");
        uPbfraction.setGrainID("TestFraction");
        uPbfraction.setZircon(true);

//        aliquot.getMineralStandardModels().add( new MineralStandardModel( "Test Model" ) );
        SampleDateModel temp1 = new SampleDateModel(
                "WM208_232",
                "WM208_232",
                "",
                new BigDecimal("1.1"),
                "PCT",
                new BigDecimal("0.222"));

        temp1.setPreferred(true);
        aliquot.getSampleDateModels().add(temp1);

        ((UPbReduxAliquot) aliquot).getAliquotFractions().clear();
        ((UPbReduxAliquot) aliquot).getAliquotFractions().add(uPbfraction);
        temp1.setAliquot(aliquot);
        //     temp1.PopulateFractionVector();

        ((UPbReduxAliquot) aliquot).prepareUPbReduxAliquotForXMLSerialization();

        String testFileName = "UPbReduxAliquotTEST.xml";

        ((UPbReduxAliquot) aliquot).serializeXMLObject(testFileName);
        ((UPbReduxAliquot) aliquot).readXMLObject(testFileName, true);
    }
    
}
