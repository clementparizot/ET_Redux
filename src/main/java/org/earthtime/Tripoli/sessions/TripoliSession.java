/*
 * TripoliSession.java
 *
 * Created Jul 1, 2011
 *
 * Copyright 2006-2015 James F. Bowring and www.Earth-Time.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.earthtime.Tripoli.sessions;

import Jama.Matrix;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.earthtime.Tripoli.dataModels.DataModelFitFunctionInterface;
import org.earthtime.Tripoli.dataModels.DataModelInterface;
import org.earthtime.Tripoli.dataModels.DownholeFractionationDataModel;
import org.earthtime.Tripoli.dataModels.MaskingSingleton;
import org.earthtime.Tripoli.dataModels.RawRatioDataModel;
import org.earthtime.Tripoli.dataModels.collectorModels.IonCounterCollectorModel;
import org.earthtime.Tripoli.dataModels.sessionModels.AbstractSessionForStandardDataModel;
import org.earthtime.Tripoli.dataModels.sessionModels.SessionCorrectedUnknownsSummary;
import org.earthtime.Tripoli.dataModels.sessionModels.SessionForStandardDataModelDownholeFractionation;
import org.earthtime.Tripoli.dataModels.sessionModels.SessionForStandardDataModelInterceptFractionation;
import org.earthtime.Tripoli.fitFunctions.AbstractFunctionOfX;
import org.earthtime.Tripoli.fractions.TripoliFraction;
import org.earthtime.Tripoli.massSpecSetups.AbstractMassSpecSetup;
import org.earthtime.Tripoli.rawDataFiles.handlers.AbstractRawDataFileHandler;
import org.earthtime.Tripoli.samples.AbstractTripoliSample;
import org.earthtime.UPb_Redux.ReduxConstants;
import org.earthtime.UPb_Redux.exceptions.BadLabDataException;
import org.earthtime.UPb_Redux.fractions.Fraction;
import org.earthtime.UPb_Redux.fractions.UPbReduxFractions.UPbFractionI;
import org.earthtime.UPb_Redux.fractions.UPbReduxFractions.UPbLAICPMSFraction;
import org.earthtime.UPb_Redux.reduxLabData.ReduxLabData;
import org.earthtime.UPb_Redux.valueModels.ValueModel;
import org.earthtime.dataDictionaries.FitFunctionTypeEnum;
import org.earthtime.dataDictionaries.FractionSelectionTypeEnum;
import org.earthtime.dataDictionaries.FractionationTechniquesEnum;
import org.earthtime.dataDictionaries.IncludedTypeEnum;
import org.earthtime.dataDictionaries.MineralStandardUPbConcentrationsPPMEnum;
import org.earthtime.dataDictionaries.RadRatios;
import org.earthtime.dataDictionaries.RawRatioNames;
import org.earthtime.ratioDataModels.AbstractRatiosDataModel;
import org.earthtime.ratioDataModels.initialPbModelsET.StaceyKramersInitialPbModelET;
import org.earthtime.ratioDataModels.initialPbModelsET.commonLeadLossCorrectionSchemes.CommonLeadLossCorrectionSchemeNONE;
import org.earthtime.ratioDataModels.mineralStandardModels.MineralStandardUPbModel;
import org.earthtime.ratioDataModels.physicalConstantsModels.PhysicalConstantsModel;

/**
 *
 * @author James F. Bowring
 */
public class TripoliSession implements
        TripoliSessionInterface,
        Serializable {

    // Class variables
    private static final long serialVersionUID = 5508404064655599158L;
    private AbstractRawDataFileHandler rawDataFileHandler;
    private SortedSet<TripoliFraction> tripoliFractions;
    private ArrayList<AbstractTripoliSample> tripoliSamples;
    private SortedMap<RawRatioNames, DownholeFractionationDataModel> downholeFractionationDataModels;
    private SortedMap<RawRatioNames, AbstractSessionForStandardDataModel> sessionForStandardsDownholeFractionation;
    private SortedMap<RawRatioNames, AbstractSessionForStandardDataModel> sessionForStandardsInterceptFractionation;
    private AbstractRatiosDataModel primaryMineralStandard;
    private FractionationTechniquesEnum fractionationTechnique;
    //feb 2013 ... refactor by promoting common attributes
    private double[] timesForStandards;
    private double[] timesForUnknowns;
    private double[] timesForPlotting;
    private Matrix matrixJgammag;
    private ArrayList<Double> tList;
    double[] h;
    private Matrix Q;
    private Matrix R;
    private Map<FitFunctionTypeEnum, Matrix> matrixJfMapPlotting;
    private Map<FitFunctionTypeEnum, Matrix> matrixJfMapUnknowns;
    // mar 2013 
    private MaskingSingleton maskingSingleton;
    private int estimatedPlottingPointsCount;
    // march 2013 modernizing approach to encapsulate what is sent to redux
    private SortedMap<RadRatios, SessionCorrectedUnknownsSummary> sessionCorrectedUnknownsSummaries;
    // april 2014
    private boolean dataProcessed;
    // dec 2014
    private int leftShadeCount;
    private boolean fitFunctionsUpToDate;

    /**
     *
     */
    protected boolean calculatedInitialFitFunctions;
    // jan 2014

    /**
     *
     */
    protected String commonLeadCorrectionHighestLevel;

    /**
     *
     *
     * @param rawDataFileHandler
     * @param tripoliSamples
     */
    @SuppressWarnings("MapReplaceableByEnumMap")
    public TripoliSession( //
            AbstractRawDataFileHandler rawDataFileHandler, //
            ArrayList<AbstractTripoliSample> tripoliSamples) {

        this.rawDataFileHandler = rawDataFileHandler;
        this.tripoliSamples = tripoliSamples;

        this.tripoliFractions = rawDataFileHandler.getTripoliFractions();
        this.downholeFractionationDataModels = new TreeMap<>();
        this.sessionForStandardsDownholeFractionation = new TreeMap<>();
        this.sessionForStandardsInterceptFractionation = new TreeMap<>();
        this.fractionationTechnique = FractionationTechniquesEnum.DOWNHOLE;//.INTERCEPT;
        this.sessionCorrectedUnknownsSummaries = new TreeMap<>();

        this.calculatedInitialFitFunctions = false;

        try {
            this.primaryMineralStandard = ReduxLabData.getInstance().getDefaultLAICPMSPrimaryMineralStandardModel();
        } catch (BadLabDataException badLabDataException) {
        }

        this.leftShadeCount = 0;

        this.fitFunctionsUpToDate = false;
    }

    /**
     *
     */
    @Override
    public void updateFractionsToSampleMembership() {
        tripoliSamples.stream().forEach((tripoliSample) -> {
            SortedSet<TripoliFraction> tripoliFractionsOfSample = tripoliSample.getSampleFractions();
            for (TripoliFraction tf : tripoliFractionsOfSample) {
                tf.setStandard(tripoliSample.isPrimaryStandard());
                // nov 2014
                tf.setSampleR238_235s(tripoliSample.getSampleR238_235s());
            }
        });
    }

    /**
     *
     * @param leftShadeCount the value of leftShadeCount
     */
    @SuppressWarnings("MapReplaceableByEnumMap")
    @Override
    public void processRawData(int leftShadeCount) {
        // backwards check for existing projects Sept 2012
        if (fractionationTechnique == null) {
            fractionationTechnique = FractionationTechniquesEnum.INTERCEPT;
        }

        if ((tripoliFractions != null) && (primaryMineralStandard != null)) {

            // may 2014 for live data
            if (sessionForStandardsInterceptFractionation == null) {
                this.sessionForStandardsInterceptFractionation = new TreeMap<>();
            }
            if (sessionForStandardsDownholeFractionation == null) {
                this.sessionForStandardsDownholeFractionation = new TreeMap<>();
            }
            //create zero-based time stamp for fractions to use in session view
            long firstFractionTimeStamp = tripoliFractions.first().getPeakTimeStamp();

            // create map of primaryStandards values to be used to update each fraction's ratios
            SortedSet<DataModelInterface> ratiosSortedSet = tripoliFractions.first().getRatiosForFractionFitting();//.getValidRawRatios();
            Double[] standardValuesMap = new Double[ratiosSortedSet.size()];
            int count = 0;

            Iterator<DataModelInterface> ratiosSortedSetIterator = ratiosSortedSet.iterator();
            while (ratiosSortedSetIterator.hasNext()) {
                DataModelInterface ratio = ratiosSortedSetIterator.next();
                String ratioName = ratio.getRawRatioModelName().getName().replace("w", "r");
                ValueModel standardRatio = primaryMineralStandard.getDatumByName(ratioName);
                if (standardRatio != null) {
                    standardValuesMap[count] = standardRatio.getValue().doubleValue();
                } else {
                    standardValuesMap[count] = 0.0;
                }
                count++;
            }

            Iterator<TripoliFraction> fractionIterator = tripoliFractions.iterator();
            while (fractionIterator.hasNext()) {

                TripoliFraction tf = fractionIterator.next();

                // give each ratio within each fraction the matching standard value
                // oct 2012 this call also resets the datamodel as to whether it is a valid fractionation-correcting ratio
                tf.updateRawRatioDataModelsWithPrimaryStandardValue(standardValuesMap);

                // give each fraction a normalized time stamp from beginning of peak readings of first fraction
                // feb 2013 temp hack to shift fractions slightly for calculating and plotting --- need to fix underlying 2 second shift issue
                tf.setZeroBasedNormalizedTimeStamp(//
                        (tf.getPeakTimeStamp() - firstFractionTimeStamp) / rawDataFileHandler.getMassSpec().getCollectorDataFrequencyMillisecs());// + 2L);
                tf.setZeroBasedTimeStamp((tf.getPeakTimeStamp() - firstFractionTimeStamp + 2L * rawDataFileHandler.getMassSpec().getCollectorDataFrequencyMillisecs()));
            }

            // feb 2013
            // find count of onpeak and setup masking shade parallels dataactive >> to be shaded means to be false
            maskingSingleton = MaskingSingleton.getInstance();
            maskingSingleton.setMaskingArray(AbstractMassSpecSetup.defaultDataActiveMap(tripoliFractions.first().getDataActiveMap().length));

            // choice based on current fractionation technique
            // create downholeFractionationDataModels
            downholeFractionationDataModels = //
                    rawDataFileHandler.getMassSpec().downholeFractionationAlphaDataModelsFactory(tripoliFractions);

            // may 2014 - for use with live data, we check to see if these exist and if so, do not recreate
            if ((sessionForStandardsInterceptFractionation.size() == 0) || (sessionForStandardsDownholeFractionation.size() == 0)) {
                // create sessionForStandards models for both downhole and intercept methods
                Iterator<DataModelInterface> dataModelIterator = tripoliFractions.first().getValidRawRatioAlphas().iterator();
                while (dataModelIterator.hasNext()) {
                    DataModelInterface dm = dataModelIterator.next();
                    AbstractSessionForStandardDataModel ssm = //
                            new SessionForStandardDataModelDownholeFractionation( //
                                    this, //
                                    dm.getRawRatioModelName(), dm.getStandardValue(), //
                                    getTripoliFractionsFiltered(FractionSelectionTypeEnum.STANDARD, IncludedTypeEnum.ALL));

                    sessionForStandardsDownholeFractionation.put(dm.getRawRatioModelName(), ssm);

                    ssm = //
                            new SessionForStandardDataModelInterceptFractionation( //
                                    this, //
                                    dm.getRawRatioModelName(), dm.getStandardValue(), //
                                    getTripoliFractionsFiltered(FractionSelectionTypeEnum.STANDARD, IncludedTypeEnum.ALL));

                    sessionForStandardsInterceptFractionation.put(dm.getRawRatioModelName(), ssm);
                }
            } else {
                // may 2014 handling case of live data
                Iterator<DataModelInterface> dataModelIterator = tripoliFractions.first().getValidRawRatioAlphas().iterator();
                while (dataModelIterator.hasNext()) {
                    DataModelInterface dm = dataModelIterator.next();

                    AbstractSessionForStandardDataModel ssm = sessionForStandardsInterceptFractionation.get(dm.getRawRatioModelName());
                    ssm.setStandardFractions(getTripoliFractionsFiltered(FractionSelectionTypeEnum.STANDARD, IncludedTypeEnum.ALL));

                    ssm = sessionForStandardsDownholeFractionation.get(dm.getRawRatioModelName());
                    ssm.setStandardFractions(getTripoliFractionsFiltered(FractionSelectionTypeEnum.STANDARD, IncludedTypeEnum.ALL));

                }
            }

//////////////////            calculateDownholeFitSummariesForPrimaryStandard();
//            generateInterceptFitFunctionsForPrimaryStandard();
            dataProcessed = true;
        } else {
            dataProcessed = false;
        }
    }

    @Override
    public void postProcessDataForCommonLeadLossPreparation() {
        // nov 2014 post processing of */204 ratios to remove negative value from active data
        // also forces undoing of any re-selection
        SortedSet<TripoliFraction> allFractions = getTripoliFractionsFiltered(FractionSelectionTypeEnum.ALL, IncludedTypeEnum.INCLUDED);
        Iterator<TripoliFraction> allFractionsIterator = allFractions.iterator();
        while (allFractionsIterator.hasNext()) {
            TripoliFraction tf = allFractionsIterator.next();
//                    System.out.print("CHANGING DATAMAP for " + tf.getFractionID() + " >> ");
            tf.postProcessCommonLeadCorrectionRatios();
        }
    }

    /**
     *
     */
    @Override
    public void calculateDownholeFitSummariesForPrimaryStandard() {

        Iterator downholeFractionationDataModelIterator = downholeFractionationDataModels.keySet().iterator();
        while (downholeFractionationDataModelIterator.hasNext()) {
            RawRatioNames rrName = (RawRatioNames) downholeFractionationDataModelIterator.next();
            DownholeFractionationDataModel downholeFractionationDataModel = downholeFractionationDataModels.get(rrName);

            Iterator<TripoliFraction> fractionIterator = getTripoliFractionsFiltered(FractionSelectionTypeEnum.STANDARD, IncludedTypeEnum.INCLUDED).iterator();

            downholeFractionationDataModel.calculateWeightedMeanOfStandards(fractionIterator);

//            // dec 2012 check if masking array is all false - it was modified in previous call
//            if ( downholeFractionationDataModel.getMaskingSingleton().maskingArrayContainsTruth() ) {
            downholeFractionationDataModel.generateSetOfFitFunctions(true, false);
            calculatedInitialFitFunctions = true;

            // now calculate session statistics based on this selected fit model
            // this mean for each standard, calculate the weighted mean of the residuals
            AbstractFunctionOfX downHoleFofX = downholeFractionationDataModel.getSelectedFitFunction();
//            ((DownholeFractionationDataModel)downholeFractionationDataModel).prepareMatrixJfMapAquisitionsAquisitions();
//            
//            Matrix matrixJfStandardsStandards = getMatrixJfStandardsStandards(downHoleFofX.getShortName());
//            // this call to interpolated variances is needed ro grt Sf = need to refactor
//            downHoleFofX.calculateInterpolatedVariances(matrixJfStandardsStandards, timesForStandards);
//            
//            Matrix matrixSf = downHoleFofX.getMatrixSf();

            fractionIterator = getTripoliFractionsFiltered(FractionSelectionTypeEnum.STANDARD, IncludedTypeEnum.INCLUDED).iterator();

            downholeFractionationDataModel.calculateWeightedMeanForEachStandard(fractionIterator, rrName, downHoleFofX);
        }
//        }
    }

    /**
     *
     */
    @Override
    public void calculateSessionFitFunctionsForPrimaryStandard() {

        if (prepareMatrixJfMapUnknowns(FractionSelectionTypeEnum.UNKNOWN) //
                && prepareMatrixJfPlotting()) {

            if (fractionationTechnique.compareTo(FractionationTechniquesEnum.INTERCEPT) == 0) {
                sessionForStandardsInterceptFractionation.keySet().stream().forEach((rrName) -> {
                    try {
                        sessionForStandardsInterceptFractionation.get(rrName).generateSetOfFitFunctions(true, false);
                        // jan 2015
                        applyCorrections();
                        fitFunctionsUpToDate = true;
                    } catch (Exception e) {
                        System.out.println("Session Standards Intercept Fractionation Failed");
                    }
                });
            }
//            else if (fractionationTechnique.compareTo(FractionationTechniquesEnum.DOWNHOLE) == 0) {
//                for (RawRatioNames rrName : sessionForStandardsDownholeFractionation.keySet()) {
////april 2014                    sessionForStandardsDownholeFractionation.get(rrName).generateSetOfFitFunctions(true, false);
//                }
//            }
        }
    }

    private boolean prepareSessionWithStandardsTimes() {
        // jan 2013
        boolean retVal;
        //**********  ALL OF THIS IS CURRENTLY DONE INSIDE SPLINE FIT BUT COULD BE DONE OUTSIDE ******
        // build and calculate t and h
        SortedSet<TripoliFraction> standardFractions = getTripoliFractionsFiltered(FractionSelectionTypeEnum.STANDARD, IncludedTypeEnum.INCLUDED);//.ALL );
        int countOfStandards = standardFractions.size();

        if (countOfStandards > 0) {
            retVal = true;
            timesForStandards = new double[countOfStandards];
            tList = new ArrayList<>();
            int index = 0;
            Iterator<TripoliFraction> standardFractionIterator = standardFractions.iterator();

            while (standardFractionIterator.hasNext()) {
                TripoliFraction tf = standardFractionIterator.next();

                timesForStandards[index] = tf.getZeroBasedNormalizedTimeStamp();
                tList.add(timesForStandards[index]);

                index++;
            }

            // time deltas 
            h = new double[countOfStandards - 1];

            for (int i = 0; i < (countOfStandards - 1); i++) {
                h[i] = timesForStandards[i + 1] - timesForStandards[i];
            }
            Q = new Matrix(countOfStandards, countOfStandards - 1);
            R = new Matrix(countOfStandards - 1, countOfStandards - 1);

            for (int j = 2; j < countOfStandards - 1; j++) {
                int i = j - 1;

                Q.set(i - 1, i, 1.0 / h[i - 1]);
                Q.set(i, i, -(1.0 / h[i - 1] + 1.0 / h[i]));
                Q.set(i + 1, i, 1.0 / h[i]);

                R.set(i, i, (h[i - 1] + h[i]) / 3.0);
                R.set(i, i + 1, h[i] / 6.0);
                R.set(i + 1, i, h[i] / 6.0);
            }

            int i = countOfStandards - 2;
            Q.set(i - 1, i, 1.0 / h[i - 1]);
            Q.set(i, i, -(1.0 / h[i - 1] + 1.0 / h[i]));
            Q.set(i + 1, i, 1.0 / h[i]);

            R.set(i, i, (h[i - 1] + h[i]) / 3.0);

            Q = Q.getMatrix(0, Q.getRowDimension() - 1, 1, Q.getColumnDimension() - 1);
            R = R.getMatrix(1, R.getRowDimension() - 1, 1, R.getColumnDimension() - 1);

            Matrix JgammagSmall = R.solve(Q.transpose());

            // need to add row of zeros to top and bottom
            matrixJgammag = new Matrix(JgammagSmall.getRowDimension() + 2, JgammagSmall.getColumnDimension());
            matrixJgammag.setMatrix(1, JgammagSmall.getRowDimension() - 1, 0, JgammagSmall.getColumnDimension() - 1, JgammagSmall);
        } else {
            retVal = false;
        }

        return retVal;
    }

    /**
     *
     * @param fractionSelectionTypeEnum the value of fractionSelectionTypeEnum
     * @return the boolean
     */
    @Override
    public boolean prepareMatrixJfMapUnknowns(FractionSelectionTypeEnum fractionSelectionTypeEnum) {
        boolean retVal;
        // build session-wide marixJf for each type of fit function
        matrixJfMapUnknowns = new EnumMap<>(FitFunctionTypeEnum.class);

        // be sure times are prepared for standards AD all the work needed to support smoothing splines
        //if ( matrixJgammag == null ) {
        // we are now recalculating Jf each time to accomodate changes in included standards ... could be streamlined to only happen on count
        boolean ableToPrepareSessionWithStandardsTimes;
        try {
            ableToPrepareSessionWithStandardsTimes = prepareSessionWithStandardsTimes();
        } catch (Exception e) {
            ableToPrepareSessionWithStandardsTimes = false;
        }

        if (ableToPrepareSessionWithStandardsTimes) {
            retVal = true;
            //
            // prepare unknowns
            SortedSet<TripoliFraction> unknownFractions = getTripoliFractionsFiltered(fractionSelectionTypeEnum, IncludedTypeEnum.INCLUDED);
            int countOfUnknowns = unknownFractions.size();
            int countOfStandards = timesForStandards.length;

            int index = 0;
            Iterator<TripoliFraction> unknownFractionIterator = unknownFractions.iterator();

            // dec 2014 ... partial refactoring to ensure that global timeForUnknowns persists
            double[] timesForFractions;
            if (fractionSelectionTypeEnum.compareTo(FractionSelectionTypeEnum.UNKNOWN) == 0) {
                timesForFractions = calculateTimesForUnknowns(countOfUnknowns, unknownFractionIterator);
            } else {
                timesForFractions = timesForStandards;
            }

            // prepare spline Jf
            Matrix Jfg = new Matrix(countOfUnknowns, countOfStandards);
            Matrix Jfgamma = new Matrix(countOfUnknowns, countOfStandards);

            for (int k = 0; k < timesForFractions.length; k++) {
                populateJacobianGAndGamma(k, timesForFractions[k], Jfg, Jfgamma);
            }

            Matrix Jf = Jfg.plus(Jfgamma.times(matrixJgammag));
            matrixJfMapUnknowns.put(FitFunctionTypeEnum.SMOOTHING_SPLINE, Jf);

            // prepare LM exponential Jf
            Jf = new Matrix(countOfUnknowns, 3);
            // the LM fit function will have to populate Jf on demand as it depends on the fit parameters
            matrixJfMapUnknowns.put(FitFunctionTypeEnum.EXPONENTIAL, Jf);

            // prepare line Jf
            Jf = new Matrix(countOfUnknowns, 2, 1.0);
            for (int i = 0; i < countOfUnknowns; i++) {
                Jf.set(i, 1, timesForFractions[i]);
            }
            matrixJfMapUnknowns.put(FitFunctionTypeEnum.LINE, Jf);

            // mean has no Jf
            matrixJfMapUnknowns.put(FitFunctionTypeEnum.MEAN, null);
        } else {
            retVal = false;
        }
        return retVal;
    }

    private double[] calculateTimesForUnknowns(int countOfUnknowns, Iterator<TripoliFraction> unknownFractionIterator) {

        timesForUnknowns = new double[countOfUnknowns];

        int index = 0;

        while (unknownFractionIterator.hasNext()) {
            TripoliFraction tf = unknownFractionIterator.next();

            timesForUnknowns[index] = tf.getZeroBasedNormalizedTimeStamp();

            index++;
        }

        return timesForUnknowns;
    }

    /**
     *
     * @return
     */
    @Override
    public boolean prepareMatrixJfPlotting() {
        boolean retVal;
        // build session-wide marixJf for each type of fit function
        matrixJfMapPlotting = new EnumMap<>(FitFunctionTypeEnum.class);

        // be sure times are prepared for standards AND all the work needed to support smoothing splines
        //if ( matrixJgammag == null ) {
        // we are now recalculating Jf each time to accomodate changes in included standards ... could be streamlined to only happen on count
        if (prepareSessionWithStandardsTimes()) {
            //}
            retVal = true;
            int countOfStandards = timesForStandards.length;

            // set up plotpoints
            // build array of times of plotpoints for generating covariance with standards
            double dataMaxX = timesForStandards[timesForStandards.length - 1];
            double dataMinX = timesForStandards[0];

//            int xStep = (int) Math.floor((int) dataMaxX / estimatedPlottingPointsCount);
            int xStep = (int) Math.floor((double) dataMaxX / (double) estimatedPlottingPointsCount);

            // june 2013
            if (xStep == 0) {
                xStep = 1;
            }
            int countOfDataPoints = (int) Math.ceil(dataMaxX / xStep);

            Matrix Jfg = new Matrix(countOfDataPoints + 1, countOfStandards);
            Matrix Jfgamma = new Matrix(countOfDataPoints + 1, countOfStandards);

            timesForPlotting = new double[countOfDataPoints + 1];

            for (int t = (int) dataMinX; t < dataMaxX; t += xStep) {

                int k = t / xStep;
                timesForPlotting[k] = t;

                double tInt = t;
                populateJacobianGAndGamma(k, tInt, Jfg, Jfgamma);
            }

            // last point
            timesForPlotting[countOfDataPoints] = dataMaxX;

            double tInt = dataMaxX;
            populateJacobianGAndGamma(countOfDataPoints, tInt, Jfg, Jfgamma);

            Matrix Jf = Jfg.plus(Jfgamma.times(matrixJgammag));
            matrixJfMapPlotting.put(FitFunctionTypeEnum.SMOOTHING_SPLINE, Jf);

            // prepare LM exponential Jf
            Jf = new Matrix(countOfDataPoints + 1, 3);
            // the LM fit function will have to populate Jf on demand as it depends on the fit parameters
            matrixJfMapPlotting.put(FitFunctionTypeEnum.EXPONENTIAL, Jf);

            // prepare line Jf
            Jf = new Matrix(countOfDataPoints + 1, 2, 1.0);
            for (int i = 0; i < countOfDataPoints + 1; i++) {
                Jf.set(i, 1, timesForPlotting[i]);
            }
            matrixJfMapPlotting.put(FitFunctionTypeEnum.LINE, Jf);

            // mean has no Jf
            matrixJfMapPlotting.put(FitFunctionTypeEnum.MEAN, null);
        } else {
            retVal = false;
        }
        return retVal;
    }

    private void populateJacobianGAndGamma(int k, double tInt, Matrix Jfg, Matrix Jfgamma) {
        int stIndex = AbstractFunctionOfX.calculateTimeLeftBracketIndex(tInt, tList);

        Jfg.set(k, stIndex, (tList.get(stIndex + 1) - tInt) / h[stIndex]);
        Jfg.set(k, stIndex + 1, (tInt - tList.get(stIndex)) / h[stIndex]);
        Jfgamma.set(k, stIndex, (tInt - tList.get(stIndex)) * (tInt - tList.get(stIndex + 1)) * (h[stIndex] - tInt + tList.get(stIndex + 1)) / (6 * h[stIndex]));
        Jfgamma.set(k, stIndex + 1, (tInt - tList.get(stIndex)) * (tInt - tList.get(stIndex + 1)) * (h[stIndex] + tInt - tList.get(stIndex)) / (6 * h[stIndex]));

    }

    /**
     *
     *
     * @param fitFunctionType the value of fitFunctionType
     * @return the matrixJfPlotting updated for active standards
     */
    @Override
    public Matrix getMatrixJfPlottingActiveStandards(FitFunctionTypeEnum fitFunctionType) {

        Matrix Jf = null;
        if (matrixJfMapPlotting != null) {
            Jf = matrixJfMapPlotting.get(fitFunctionType);
        }

        return Jf;
    }

    /**
     *
     * @param fitFunctionType
     * @return
     */
    public Matrix getMatrixJfUnknownsActiveStandards(FitFunctionTypeEnum fitFunctionType) {

        Matrix Jf = null;
        if (matrixJfMapUnknowns != null) {
            Jf = matrixJfMapUnknowns.get(fitFunctionType);
        }

        return Jf;
    }

    /**
     *
     */
    @Override
    public void applyCorrections() {

        // dec 2014 - initialize fractions for rho calcs and common lead correction
        SortedSet<TripoliFraction> allFractions = getTripoliFractionsFiltered(FractionSelectionTypeEnum.ALL, IncludedTypeEnum.ALL);
        Iterator<TripoliFraction> allFractionsIterator = allFractions.iterator();

        while (allFractionsIterator.hasNext()) {
            TripoliFraction tf = allFractionsIterator.next();
            Fraction upbFraction = tf.getuPbFraction();
            if (upbFraction == null) {
                System.out.println("Missing upbFraction for " + tf.getFractionID());
            } else {
                ((UPbLAICPMSFraction) upbFraction).setSfciTotal(null);
                ((UPbLAICPMSFraction) upbFraction).initializeUpperPhiMap();
                upbFraction.getRadiogenicIsotopeRatioByName("rhoR206_238r__r207_235r").setValue(ReduxConstants.NO_RHO_FLAG);
                upbFraction.getRadiogenicIsotopeRatioByName("rhoR207_206r__r238_206r").setValue(ReduxConstants.NO_RHO_FLAG);
                upbFraction.getRadiogenicIsotopeRatioByName("rhoR206_238PbcCorr__r207_235PbcCorr").setValue(ReduxConstants.NO_RHO_FLAG);
                upbFraction.getRadiogenicIsotopeRatioByName("rhoR207_206PbcCorr__r238_206PbcCorr").setValue(ReduxConstants.NO_RHO_FLAG);
            }
        }

        calculateUThConcentrationsForUnknowns();

        sessionCorrectedUnknownsSummaries = new TreeMap<>();

        if (fractionationTechnique.compareTo(FractionationTechniquesEnum.INTERCEPT) == 0) {
            applyFractionationCorrectionsForIntercept();
        }//        } else if (fractionationTechnique.compareTo(FractionationTechniquesEnum.DOWNHOLE) == 0) {
//////////            applyFractionationCorrectionsForDownhole();
        // }
    }

    private void calculateUThConcentrationsForUnknowns() {
        // dec 2014 from appendix C
        // each unknown's concentrtations are calculted using time and braketing standards
        SortedSet<TripoliFraction> includedFractions = getTripoliFractionsFiltered(FractionSelectionTypeEnum.ALL, IncludedTypeEnum.INCLUDED);

        TripoliFraction leftStandard = null;
        boolean lookingForLeftStandard = true;
        TripoliFraction rightStandard;
        ArrayList<TripoliFraction> bracketedUnknowns = new ArrayList<>();

        if (primaryMineralStandard != null) {
            for (TripoliFraction tf : includedFractions) {
//            System.out.println(tf.getFractionID() + "  " + tf.getZeroBasedTimeStamp() + "  " + tf.isStandard());
                if (lookingForLeftStandard && tf.isStandard()) {
                    leftStandard = tf;
                    if (bracketedUnknowns.size() > 0) {
                        // we had leading unknowns and are using "leftstandard" which is actually to the right of the leading unknowns
                        for (TripoliFraction unknown : bracketedUnknowns) {
                            double unkownIntensityU = unknown.calculateMeanOfHighestIntensityU();
                            double unkownIntensityTh = unknown.calculateMeanOfHighestIntensityTh();
                            double leftStandardIntensityU = leftStandard.calculateMeanOfHighestIntensityU();
                            double leftStandardIntensityTh = leftStandard.calculateMeanOfHighestIntensityTh();

                            // concUnknown = ix * concStandard / i1 
                            // where concUnknown is the concentration of the unknown, concStandard is the U or Th standard from the mineral standard model
                            double primaryStdConcU = ((MineralStandardUPbModel) primaryMineralStandard).getConcentrationByName(MineralStandardUPbConcentrationsPPMEnum.concU238ppm.getName()).getValue().doubleValue();
                            double concUnknownU = //
                                    primaryStdConcU //
                                    * unkownIntensityU //
                                    / (leftStandardIntensityU);

                            double primaryStdConcTh = ((MineralStandardUPbModel) primaryMineralStandard).getConcentrationByName(MineralStandardUPbConcentrationsPPMEnum.concTh232ppm.getName()).getValue().doubleValue();
                            double concUnknownTh = //
                                    primaryStdConcTh //
                                    * unkownIntensityTh //
                                    / (leftStandardIntensityTh);

                            // record concentrations into fraction
                            unknown.getuPbFraction().getCompositionalMeasureByName(MineralStandardUPbConcentrationsPPMEnum.concU238ppm.getName()).setValue(new BigDecimal(concUnknownU / 1e6));
                            unknown.getuPbFraction().getCompositionalMeasureByName(MineralStandardUPbConcentrationsPPMEnum.concTh232ppm.getName()).setValue(new BigDecimal(concUnknownTh / 1e6));
                            double rTh_Usample = 0.0;
                            if (concUnknownU != 0.0) {
                                rTh_Usample = concUnknownTh / concUnknownU;
                            }
                            unknown.getuPbFraction().getCompositionalMeasureByName(MineralStandardUPbConcentrationsPPMEnum.rTh_Usample.getName()).setValue(new BigDecimal(rTh_Usample));
                        }
                        // lose the leading unknowns
                        bracketedUnknowns = new ArrayList<>();
                    }
                } else if (lookingForLeftStandard && !tf.isStandard()) {
                    if (leftStandard == null) {
                        // we have leading unknowns
                        bracketedUnknowns.add(tf);
                    } else {
                        lookingForLeftStandard = false;
                        bracketedUnknowns.add(tf);
                    }
                } else if (!lookingForLeftStandard && !tf.isStandard()) {
                    bracketedUnknowns.add(tf);
                } else if (!lookingForLeftStandard && tf.isStandard()) {
                    // we have collected the bracketed unknowns
                    rightStandard = tf;
                    double leftStandardIntensityU = 0.0;
                    double leftStandardIntensityTh = 0.0;
                    if (leftStandard != null) {
                        leftStandardIntensityU = leftStandard.calculateMeanOfHighestIntensityU();
                        leftStandardIntensityTh = leftStandard.calculateMeanOfHighestIntensityTh();
                    }
                    double rightStandardIntensityU;
                    double rightStandardIntensityTh;
                    rightStandardIntensityU = rightStandard.calculateMeanOfHighestIntensityU();
                    rightStandardIntensityTh = rightStandard.calculateMeanOfHighestIntensityTh();

                    for (TripoliFraction unknown : bracketedUnknowns) {
                        double unkownIntensityU = unknown.calculateMeanOfHighestIntensityU();
                        double unkownIntensityTh = unknown.calculateMeanOfHighestIntensityTh();

                        // concUnknown = concStandard * ix / ( i1 + (t2 - tu)/(t2 - t1) * (i2 - i1) )
                        // where concUnknown is the concentration of the unknown, concStandard is the U or Th standard from the mineral standard model
                        long leftStandardTime = 0;
                        try {
                            leftStandardTime = leftStandard.getZeroBasedTimeStamp();
                        } catch (Exception e) {
                        }
                        long rightStandardTime = 0;
                        try {
                            rightStandardTime = rightStandard.getZeroBasedTimeStamp();
                        } catch (Exception e) {
                        }

                        double primaryStdConcU = ((MineralStandardUPbModel) primaryMineralStandard).getConcentrationByName(MineralStandardUPbConcentrationsPPMEnum.concU238ppm.getName()).getValue().doubleValue();
                        double concUnknownU = //
                                primaryStdConcU //
                                * unkownIntensityU //
                                / (leftStandardIntensityU //
                                + ((double) (rightStandardTime - unknown.getZeroBasedTimeStamp()) / (double) (rightStandardTime - leftStandardTime)) * (rightStandardIntensityU - leftStandardIntensityU));

                        double primaryStdConcTh = ((MineralStandardUPbModel) primaryMineralStandard).getConcentrationByName(MineralStandardUPbConcentrationsPPMEnum.concTh232ppm.getName()).getValue().doubleValue();
                        double concUnknownTh = //
                                primaryStdConcTh //
                                * unkownIntensityTh //
                                / (leftStandardIntensityTh //
                                + ((double) (rightStandardTime - unknown.getZeroBasedTimeStamp()) / (double) (rightStandardTime - leftStandardTime)) * (rightStandardIntensityTh - leftStandardIntensityTh));

                        // record concentrations into fraction
                        Fraction uPbFraction = unknown.getuPbFraction();
                        if (uPbFraction == null) {
                            System.out.println("Missing upbFraction for " + unknown.getFractionID());
                        } else {
                            uPbFraction.getCompositionalMeasureByName(MineralStandardUPbConcentrationsPPMEnum.concU238ppm.getName()).setValue(new BigDecimal(concUnknownU / 1e6));
                            uPbFraction.getCompositionalMeasureByName(MineralStandardUPbConcentrationsPPMEnum.concTh232ppm.getName()).setValue(new BigDecimal(concUnknownTh / 1e6));
                            double rTh_Usample = 0.0;
                            if (concUnknownU != 0.0) {
                                rTh_Usample = concUnknownTh / concUnknownU;
                            }
                            uPbFraction.getCompositionalMeasureByName(MineralStandardUPbConcentrationsPPMEnum.rTh_Usample.getName()).setValue(new BigDecimal(rTh_Usample));
//                    System.out.println(" >> " + unknown.getFractionID() + "  " + unknown.getZeroBasedTimeStamp() + "  " + unknown.isStandard() + "  " + concUnknownU + "  " + concUnknownTh);
                        }
                    }

                    // prepare for next set
                    leftStandard = rightStandard;
                    lookingForLeftStandard = true;
                    bracketedUnknowns = new ArrayList<>();
                }
            }
        }
    }

    private void prepareForReductionAndCommonLeadCorrection(FractionSelectionTypeEnum fractionSelectionTypeEnum) {

        SortedSet<TripoliFraction> selectedFractions = getTripoliFractionsFiltered(fractionSelectionTypeEnum, IncludedTypeEnum.INCLUDED);
        int countOfSelectedFractions = selectedFractions.size();
        // nov 2014 section 11
        // walk the sessions and build double[][] of diagonals (3 rows by countof unkowns - one row for each session ratio
        double[][] sessionRatioDiagonalSu = new double[3][countOfSelectedFractions];
        Iterator<RawRatioNames> sessionForStandardsIterator = sessionForStandardsInterceptFractionation.keySet().iterator();
        while (sessionForStandardsIterator.hasNext()) {
            RawRatioNames rrName = sessionForStandardsIterator.next();

            AbstractSessionForStandardDataModel sessionForStandard = //
                    sessionForStandardsInterceptFractionation.get(rrName);

            // get the session fit function
            AbstractFunctionOfX sessionFofX = //
                    sessionForStandard.getSelectedFitFunction();

            double[] diagonalofSu;
            if (fractionSelectionTypeEnum.compareTo(FractionSelectionTypeEnum.UNKNOWN) == 0) {
                diagonalofSu = sessionFofX.getDiagonalOfSessionUnknownsAnalyticalCovarianceSu();
            } else {
                diagonalofSu = sessionFofX.getDiagonalOfSessionStandardsAnalyticalCovarianceSu();
            }
            // the diagonal of the session Su contains a value for the ratio for each unknown
            // this value needs to be put into the unknown's Sfci at the appropriate location (see below)
            // remember ordering is: 6/7, 6/38, 8/32, (6/4, 7/4, 8/4)
            switch (rrName) {
                case r206_207w:
                    sessionRatioDiagonalSu[0] = diagonalofSu;
                    break;
                case r206_238w:
                    sessionRatioDiagonalSu[1] = diagonalofSu;
                    break;
                case r208_232w:
                    sessionRatioDiagonalSu[2] = diagonalofSu;
                    break;
                default:
            }
        }

        // need to package the common lead correction parameters and scheme and assign them to UPbLAICPMSFraction in each case
        // need strategy for stacey kramer
        Iterator<TripoliFraction> selectedFractionsIterator = selectedFractions.iterator();
        int fractionCounter = 0;
        while (selectedFractionsIterator.hasNext()) {
            TripoliFraction tf = selectedFractionsIterator.next();
            
            // undo Pbc correction for standard
            if (tf.isStandard()){
                tf.setCommonLeadLossCorrectionScheme(CommonLeadLossCorrectionSchemeNONE.getInstance());
            }
            
            SortedMap<String, ValueModel> parameters = tf.assembleCommonLeadCorrectionParameters();
            SortedMap<String, BigDecimal> parametersSK = tf.assembleStaceyKramerCorrectionParameters();

            Fraction uPbFraction = tf.getuPbFraction();
            ((UPbLAICPMSFraction) uPbFraction).setCommonLeadCorrectionParameters(parameters);
            ((UPbLAICPMSFraction) uPbFraction).setStaceyKramerCorrectionParameters(parametersSK);
            ((UPbLAICPMSFraction) uPbFraction).setUseStaceyKramer(tf.getInitialPbModelET() instanceof StaceyKramersInitialPbModelET);
            ((UPbLAICPMSFraction) uPbFraction).setCommonLeadLossCorrectionScheme(tf.getCommonLeadLossCorrectionScheme());
            ((UPbLAICPMSFraction) uPbFraction).setRadDateForSKSynch(tf.getRadDateForSKSynch());

            try {
                Matrix SfciTotal = tf.calculateUncertaintyPbcCorrections();

                // add row and column for r238_235s of tripoli sample
                Matrix SfciTotalPlus = new Matrix(SfciTotal.getRowDimension() + 1, SfciTotal.getColumnDimension() + 1, 0.0);
                SfciTotalPlus.setMatrix(0, SfciTotal.getRowDimension() - 1, 0, SfciTotal.getColumnDimension() - 1, SfciTotal);

                // now modify Sfci for fraction
                SfciTotalPlus.set(0, 0, sessionRatioDiagonalSu[0][fractionCounter]);
                SfciTotalPlus.set(1, 1, sessionRatioDiagonalSu[1][fractionCounter]);
                SfciTotalPlus.set(2, 2, sessionRatioDiagonalSu[2][fractionCounter]);

                double sampleR238_235s = tf.getSampleR238_235s().getValue().doubleValue();
                if (sampleR238_235s != 0.0) {
                    SfciTotalPlus.set(6, 6, Math.pow(tf.getSampleR238_235s().getOneSigmaAbs().doubleValue() / sampleR238_235s, 2));
                } else {
                    SfciTotalPlus.set(6, 6, 0.0);
                }

                ((UPbLAICPMSFraction) uPbFraction).setSfciTotal(SfciTotalPlus);
            } catch (Exception e) {
                // problem with matrix math
            }
            // the rest of this math occurs in fraction reduction once we are in Redux part
            fractionCounter++;
        }
    }

    @Override
    public void prepareForReductionAndCommonLeadCorrection() {
        prepareForReductionAndCommonLeadCorrection(FractionSelectionTypeEnum.STANDARD);
        prepareForReductionAndCommonLeadCorrection(FractionSelectionTypeEnum.UNKNOWN);
    }

    private void applyFractionationCorrectionsForIntercept() {
        System.out.println("\nINTERCEPT Fractionation Correction");

        // walk the sessions
        Iterator<RawRatioNames> sessionForStandardsIterator = sessionForStandardsInterceptFractionation.keySet().iterator();
        while (sessionForStandardsIterator.hasNext()) {
            RawRatioNames rrName = sessionForStandardsIterator.next();

            AbstractSessionForStandardDataModel sessionForStandard = //
                    sessionForStandardsInterceptFractionation.get(rrName);

            // get the session fit function
            AbstractFunctionOfX sessionFofX = //
                    sessionForStandard.getSelectedFitFunction();

            if (sessionFofX == null) {
                // let's do it and thus use the default fitfunction = spline unless spline generated a line when spline failed
                try {
                    sessionForStandard.generateSetOfFitFunctions(true, false);
                    sessionForStandard.setSelectedFitFunctionType(FitFunctionTypeEnum.SMOOTHING_SPLINE);
                    sessionFofX = sessionForStandard.getSelectedFitFunction();
                } catch (Exception e) {
                    System.out.println("Session for Standard NO fit functions success");
                }
            }

            // keep in this order
            fractionationCorrectFractionSetForSessionRatio(FractionSelectionTypeEnum.STANDARD, sessionFofX, sessionForStandard.getStandardValue(), rrName);
            fractionationCorrectFractionSetForSessionRatio(FractionSelectionTypeEnum.UNKNOWN, sessionFofX, sessionForStandard.getStandardValue(), rrName);
        }

        // may 2013 - reject bad fractions for user
        SortedSet<TripoliFraction> unknownFractions = getTripoliFractionsFiltered(FractionSelectionTypeEnum.UNKNOWN, IncludedTypeEnum.ALL);
        Iterator<TripoliFraction> unknownFractionIterator = unknownFractions.iterator();

        while (unknownFractionIterator.hasNext()) {
            TripoliFraction tf = unknownFractionIterator.next();
            ((UPbFractionI) tf.getuPbFraction()).setRejected(false);
            if (!tf.confirmHealthyFraction()) {
                ((UPbFractionI) tf.getuPbFraction()).setRejected(true);
                System.out.println("REJECTING " + tf.getFractionID());
            }
            if (!tf.isIncluded()) {
                ((UPbFractionI) tf.getuPbFraction()).setRejected(true);
                System.out.println("REJECTING " + tf.getFractionID());
            }
        }

    }

    /**
     *
     * @param fractionSelectionTypeEnum the value of fractionSelectionTypeEnum
     * @param sessionFofX the value of sessionFofX
     * @param sessionStandardValue the value of sessionStandardValue
     * @param rrName the value of rrName
     */
    private void fractionationCorrectFractionSetForSessionRatio(//
            FractionSelectionTypeEnum fractionSelectionTypeEnum, AbstractFunctionOfX sessionFofX, double sessionStandardValue, RawRatioNames rrName) {

        // section 11 - first pass through unknowns to build covariance matrix
        // update may 2013 **********************************************
//        System.out.println("\n\nPROCESSING RATIO " + rrName.getName());
        if (prepareMatrixJfMapUnknowns(fractionSelectionTypeEnum)) {

            SortedSet<TripoliFraction> unknownFractions = getTripoliFractionsFiltered(fractionSelectionTypeEnum, IncludedTypeEnum.INCLUDED);

            int countOfUnknowns = unknownFractions.size();
            if (countOfUnknowns > 0) {

                Matrix matrixJfUnknownsActiveStandards = getMatrixJfUnknownsActiveStandards(sessionFofX.getShortName());

                // forces update of Sf
                // TODO: Refactor
                if (fractionSelectionTypeEnum.compareTo(FractionSelectionTypeEnum.UNKNOWN) == 0) {
                    sessionFofX.calculateInterpolatedVariances(matrixJfUnknownsActiveStandards, timesForUnknowns);
                } else {
                    sessionFofX.calculateInterpolatedVariances(matrixJfUnknownsActiveStandards, timesForStandards);
                }

                Matrix matrixSf = sessionFofX.getMatrixSf();

                Matrix unknownsAnalyticalCovarianceSu = new Matrix(countOfUnknowns, countOfUnknowns);
                double[] dLrInt_dDt_Unknowns = new double[countOfUnknowns];
                Map<String, Integer> unknownFractionIDs = new HashMap<>();

                int index = 0;

                Iterator<TripoliFraction> unknownFractionIterator = unknownFractions.iterator();
                while (unknownFractionIterator.hasNext()) {
                    TripoliFraction tf = unknownFractionIterator.next();

                    unknownFractionIDs.put(tf.getFractionID(), index);

                    AbstractFunctionOfX FofX = ((DataModelFitFunctionInterface) tf.getRawRatioDataModelByName(rrName)).getSelectedFitFunction();

                    unknownsAnalyticalCovarianceSu.set(index, index, FofX.getYInterceptVariance() + sessionFofX.getOverDispersion());

                    dLrInt_dDt_Unknowns[index] = ((DataModelFitFunctionInterface) tf.getRawRatioDataModelByName(rrName)).getSelectedFitFunction().getdLrInt_dDt();

                    index++;
                }

                unknownsAnalyticalCovarianceSu.plusEquals(matrixSf);

                // check for identical ioncounters
                if (((RawRatioDataModel) unknownFractions.first().getRawRatioDataModelByName(rrName)).hasTwoIdenticalIonCounters()) {

                    double deadTimeOneSigmaAbsSqr = ((IonCounterCollectorModel) ((RawRatioDataModel) unknownFractions.first().getRawRatioDataModelByName(rrName)).getBotIsotope()//
                            .getCollectorModel()).getDeadTime().getOneSigmaAbs().movePointLeft(0).pow(2).doubleValue();

                    Matrix matrixdLrInt_dDt_Unkowns = new Matrix(dLrInt_dDt_Unknowns, dLrInt_dDt_Unknowns.length);

                    Matrix matrixSuod = matrixdLrInt_dDt_Unkowns.times(matrixdLrInt_dDt_Unkowns.transpose().times(deadTimeOneSigmaAbsSqr));

                    // zero diagonal
                    for (int i = 0; i < countOfUnknowns; i++) {
                        matrixSuod.set(i, i, 0.0);
                    }

                    unknownsAnalyticalCovarianceSu.plusEquals(matrixSuod);
                }

                // nov 2014 finally the math to calculate the rhos and Pbc correction uncertainties.
                // save Matrix unknownsAnalyticalCovarianceSu DIAGONAL for use in common lead corrections etc
                double[] diagonalOfSessionUnknownsAnalyticalCovarianceSu = new double[countOfUnknowns];
                for (int i = 0; i < countOfUnknowns; i++) {
                    diagonalOfSessionUnknownsAnalyticalCovarianceSu[i] = unknownsAnalyticalCovarianceSu.get(i, i);
                }

                if (fractionSelectionTypeEnum.compareTo(FractionSelectionTypeEnum.UNKNOWN) == 0) {
                    sessionFofX.setDiagonalOfSessionUnknownsAnalyticalCovarianceSu(diagonalOfSessionUnknownsAnalyticalCovarianceSu);
                } else {
                    sessionFofX.setDiagonalOfSessionStandardsAnalyticalCovarianceSu(diagonalOfSessionUnknownsAnalyticalCovarianceSu);
                }

                // oct 2014 handle common lead scheme B
                if (rrName.compareTo(RawRatioNames.r206_207w) == 0) {
                    applyFractionationCorrectionToUnknownRatios(fractionSelectionTypeEnum, //
                            sessionFofX, unknownFractionIDs, countOfUnknowns, unknownsAnalyticalCovarianceSu, sessionStandardValue, rrName, rrName.getName());

                    try {
                        applyFractionationCorrectionToUnknownRatios(fractionSelectionTypeEnum, //
                                sessionFofX, unknownFractionIDs, countOfUnknowns, unknownsAnalyticalCovarianceSu, sessionStandardValue, RawRatioNames.r206_204w, rrName.getName());
                    } catch (Exception e) {
                    }
                    try {
                        applyFractionationCorrectionToUnknownRatios(fractionSelectionTypeEnum, //
                                sessionFofX, unknownFractionIDs, countOfUnknowns, unknownsAnalyticalCovarianceSu, sessionStandardValue, RawRatioNames.r207_204w, rrName.getName());
                    } catch (Exception e) {
                    }
                    try {
                        applyFractionationCorrectionToUnknownRatios(fractionSelectionTypeEnum, //
                                sessionFofX, unknownFractionIDs, countOfUnknowns, unknownsAnalyticalCovarianceSu, sessionStandardValue, RawRatioNames.r208_204w, rrName.getName());
                    } catch (Exception e) {
                    }
                } else {
                    applyFractionationCorrectionToUnknownRatios(fractionSelectionTypeEnum, //
                            sessionFofX, unknownFractionIDs, countOfUnknowns, unknownsAnalyticalCovarianceSu, sessionStandardValue, rrName, rrName.getName());
                }

            } // end test if any unknowns
        }
    }

    /**
     *
     * @param fractionSelectionTypeEnum the value of fractionSelectionTypeEnum
     * @param sessionFofX the value of sessionFofX
     * @param unknownFractionIDs the value of unknownFractionIDs
     * @param countOfUnknowns the value of countOfSelectedFractions
     * @param unknownsAnalyticalCovarianceSu the value of
     * unknownsAnalyticalCovarianceSu
     * @param sessionStandardValue the value of sessionStandardValue
     * @param rrName the value of rrName
     * @param standardRatioName the value of standardRatioName
     */
    private void applyFractionationCorrectionToUnknownRatios(//
            FractionSelectionTypeEnum fractionSelectionTypeEnum, AbstractFunctionOfX sessionFofX, //
            Map<String, Integer> unknownFractionIDs, int countOfUnknowns, Matrix unknownsAnalyticalCovarianceSu, double sessionStandardValue, RawRatioNames rrName, String standardRatioName) {

        Matrix unknownsLogRatioMeans = new Matrix(countOfUnknowns, 1);

        SortedSet<TripoliFraction> unknownFractions = getTripoliFractionsFiltered(fractionSelectionTypeEnum, IncludedTypeEnum.INCLUDED);
        Iterator<TripoliFraction> unknownFractionIterator = unknownFractions.iterator();
        int index = 0;

        while (unknownFractionIterator.hasNext()) {
            TripoliFraction tf = unknownFractionIterator.next();

            if (((DataModelFitFunctionInterface) tf.getRawRatioDataModelByName(rrName)).getSelectedFitFunction() != null) {
                double measuredValue = ((DataModelFitFunctionInterface) tf.getRawRatioDataModelByName(rrName)).getSelectedFitFunction().getYIntercept();
                double interpolatedLogRatio = sessionFofX.f(tf.getZeroBasedNormalizedTimeStamp());

                double upperPhi = (Math.log(sessionStandardValue) - interpolatedLogRatio);
                // oct 2014 for common lead scheme B
                if (rrName.compareTo(RawRatioNames.r206_207w) == 0) {
                    // save transient upperPhi for use with 6/4, 7/4, 8/4
                    tf.setUpperPhi_r206_207(upperPhi);
                }
                // now special case for 6/4 7/4 8/4
                String nameOfUpperPhi = "";

                if (rrName.getName().contains("204") && !(tf.getCommonLeadLossCorrectionScheme() instanceof CommonLeadLossCorrectionSchemeNONE)) {
                    if (((RawRatioDataModel) tf.getRawRatioDataModelByName(rrName)).getBotIsotope().isForceMeanForCommonLeadRatios()) {
                        measuredValue = Math.log(measuredValue);//forced mean with more than 10% neg                   ((RawRatioDataModel) tf.getRawRatioDataModelByName(rrName)).getRatios()[0];
                    }
                    AbstractRatiosDataModel physicalConstants;
                    try {
                        physicalConstants = ReduxLabData.getInstance().getDefaultPhysicalConstantsModel();
                        double gmol204 = ((PhysicalConstantsModel) physicalConstants).getAtomicMolarMassByName("gmol204").getValue().doubleValue();
                        double gmol206 = ((PhysicalConstantsModel) physicalConstants).getAtomicMolarMassByName("gmol206").getValue().doubleValue();
                        double gmol207 = ((PhysicalConstantsModel) physicalConstants).getAtomicMolarMassByName("gmol207").getValue().doubleValue();
                        double gmol208 = ((PhysicalConstantsModel) physicalConstants).getAtomicMolarMassByName("gmol208").getValue().doubleValue();

                        double logMassRatio_206_207 = Math.log(gmol206 / gmol207);

                        if (rrName.getName().contains("206")) {
                            upperPhi = tf.getUpperPhi_r206_207() * (Math.log(gmol206 / gmol204) / logMassRatio_206_207);
                            nameOfUpperPhi = "upperPhi_r206_204";
                        } else if (rrName.getName().contains("207")) {
                            upperPhi = tf.getUpperPhi_r206_207() * (Math.log(gmol207 / gmol204) / logMassRatio_206_207);
                            nameOfUpperPhi = "upperPhi_r207_204";
                        } else { // 208
                            upperPhi = tf.getUpperPhi_r206_207() * (Math.log(gmol208 / gmol204) / logMassRatio_206_207);
                            nameOfUpperPhi = "upperPhi_r208_204";
                        }

                    } catch (BadLabDataException badLabDataException) {
                    }
                }

                double correctedLogRatio = measuredValue + upperPhi;

                unknownsLogRatioMeans.set(index, 0, correctedLogRatio);

                double correctedSigma = Math.sqrt(unknownsAnalyticalCovarianceSu.get(index, index));

                double upperTwoSigmaUncertaintyOfCorrectedRatio = Math.exp(correctedLogRatio + 2.0 * correctedSigma);
                double correctedRatio = Math.exp(correctedLogRatio);
                double lowerTwoSigmaUncertaintyOfCorrectedRatio = Math.exp(correctedLogRatio - 2.0 * correctedSigma);

                double oneSigmaOfCorrectedRatio = (upperTwoSigmaUncertaintyOfCorrectedRatio - lowerTwoSigmaUncertaintyOfCorrectedRatio) / 4.0;

                Fraction uPbFraction = tf.getuPbFraction();
                if (uPbFraction == null) {
                    System.out.println("Missing upbFraction for " + tf.getFractionID());
                } else {
                    ValueModel myMeasuredRatio = uPbFraction.getMeasuredRatioByName(rrName.getName().replace("w", "m"));
                    myMeasuredRatio.setValue(correctedRatio);
                    try {
                        // measured ratios are expecting percent uncertainties from the old tripoli so we need to convert here
                        myMeasuredRatio.setOneSigma(ValueModel.convertOneSigmaAbsToPctIfRequired(myMeasuredRatio, new BigDecimal(oneSigmaOfCorrectedRatio)));
                    } catch (Exception e) {
                        myMeasuredRatio.setOneSigma(BigDecimal.ZERO);
                    }

                    // oct 2014
                    ((UPbFractionI) uPbFraction).setRejected(!tf.isIncluded());
                    //testing oct 2014
                    if (rrName.getName().contains("204")) {
                        ((UPbLAICPMSFraction) uPbFraction).getUpperPhiMap().put(nameOfUpperPhi, upperPhi);
                    }
                }

            }
            index++;//increased even if no fit function

            try {
                ((UPbFractionI) tf.getuPbFraction()).setRejected(!tf.isIncluded());
                ((UPbFractionI) tf.getuPbFraction()).setStandard(tf.isStandard());
            } catch (Exception e) {
            }
        }

        if (primaryMineralStandard != null) {
            // pickle session for redux - these session summaries are used by sample dates to calculate weighted means
            RadRatios radRatio = RadRatios.valueOf(rrName.getName().replace("w", "r"));
            // march 2013 hack until we have ratio-flipping implemented
            ValueModel standardValueModel = primaryMineralStandard.getDatumByName(standardRatioName.replace("w", "r"));
            // todo update standard model to contain these already
            double varianceOfStandardLogRatio = standardValueModel.varianceOfLogRatio();
            // currently weighted means include 207/206 not 206/207, so we negate logratios to flip them
            if (radRatio.compareTo(RadRatios.r206_207r) == 0) {
                // oct 2014 added in handling for converted standard as well
                // need to calculate inverted standard value too
                radRatio = RadRatios.r207_206r;
                // this inverts log ratios
                unknownsLogRatioMeans.timesEquals(-1.0);
            }

            SessionCorrectedUnknownsSummary sessionCorrectedUnknownsSummary =//
                    new SessionCorrectedUnknownsSummary( //
                            unknownsAnalyticalCovarianceSu, unknownFractionIDs, //
                            unknownsLogRatioMeans,//
                            radRatio,
                            varianceOfStandardLogRatio);

            sessionCorrectedUnknownsSummaries.put(radRatio, sessionCorrectedUnknownsSummary);
        }

    }

    private void applyFractionationCorrectionsForDownhole() {
        // alpha = value of red fit line plus value of spline
        // multiply each data point by (1 + alpha)

        // calculate array of per-aquisition (say 1-15 seconds) fitted downHoleFractionation values 
        // for each ratio to use for every fraction
        // the spline values will be calculated on-th-fly since each is used only once
        System.out.println("DOWNHOLE Fractionation Correction");
        downholeFractionationDataModels.keySet().stream().forEach((rrName) -> {
            SortedSet<TripoliFraction> allFractions = getTripoliFractionsFiltered(FractionSelectionTypeEnum.STANDARD, IncludedTypeEnum.INCLUDED);

            // calculate the down hole fractionation for this ratio all fractions
            DownholeFractionationDataModel downHolefractionationAlpha = downholeFractionationDataModels.get(rrName);

//            // dec 2012 check if masking array is all false - it was modified in previous call
//            if ( downHolefractionationAlpha.getMaskingSingleton().maskingArrayContainsTruth() ) {
//
            AbstractFunctionOfX myDownHoleFofX = //
                    downHolefractionationAlpha.getSelectedDownHoleFrationationFitFunction();

            double[] downHoleFractionation = //
                    new double[downHolefractionationAlpha.getNormalizedAquireTimes().length];

            for (int i = 0; i < downHoleFractionation.length; i++) {
                downHoleFractionation[i] = myDownHoleFofX.f(downHolefractionationAlpha.getNormalizedAquireTimes()[i]);
            }

            // get the session fit function
            AbstractFunctionOfX sessionFofX = //
                    sessionForStandardsDownholeFractionation.get(rrName).getSelectedFitFunction();

            if (sessionFofX == null) {
                // let's do it and thus use the default fitfunction = spline unless spline generated a line when spline failed
                //FIX THIS FIX THIS - this call calls all          
                calculateSessionFitFunctionsForPrimaryStandard();
                sessionFofX = sessionForStandardsDownholeFractionation.get(rrName).getSelectedFitFunction();
            }
            for (TripoliFraction tf : allFractions) {
                if (!tf.getRawRatioDataModelByName(rrName).isBelowDetection()) {

                    double fractionStartAquireTime = tf.getZeroBasedNormalizedTimeStamp();
                    double[] ratios = ((RawRatioDataModel) tf.getRawRatioDataModelByName(rrName)).getRatios();
                    double[] correctedRatios = new double[ratios.length];
                    double[] onPeakAquireTimesInSeconds = downHolefractionationAlpha.getOnPeakAcquireTimesInSeconds();

                    for (int i = 0; i < onPeakAquireTimesInSeconds.length; i++) {
                        // we use currently the normalized times for alpha fitting, but need actual time for session
                        double valueOfSpline = sessionFofX.f(fractionStartAquireTime + onPeakAquireTimesInSeconds[i]);
                        double alpha = downHoleFractionation[i] + valueOfSpline;

                        correctedRatios[i] = (1.0 + alpha) * ratios[i];
                    }

                    ((RawRatioDataModel) tf.getRawRatioDataModelByName(rrName)).setCorrectedRatios(correctedRatios);
                    // calculate mean, std dev, std err, etc. for plotting
                    tf.getRawRatioDataModelByName(rrName).calculateCorrectedRatioStatistics();

                    // JULY 2012
                    // push data to UPbFractions for Redux to display
                    //TODO: clean up this hack
                    tf.getuPbFraction().getMeasuredRatioByName(rrName.getName().replace("w", "m"))//
                            .setValue(((RawRatioDataModel) tf.getRawRatioDataModelByName(rrName)).getMeanOfCorrectedRatios());
                    tf.getuPbFraction().getMeasuredRatioByName(rrName.getName().replace("w", "m"))//
                            .setOneSigma(((RawRatioDataModel) tf.getRawRatioDataModelByName(rrName)).getStdErrOfMeanCorrectedRatios());

                }
            }
        });
    }

    /**
     * @return the rawDataFileHandler
     */
    @Override
    public AbstractRawDataFileHandler getRawDataFileHandler() {
        return rawDataFileHandler;
    }

    /**
     * @param rawDataFileHandler the rawDataFileHandler to set
     */
    @Override
    public void setRawDataFileHandler(AbstractRawDataFileHandler rawDataFileHandler) {
        this.rawDataFileHandler = rawDataFileHandler;
    }

    /**
     * @return the tripoliFractions
     */
    @Override
    public SortedSet<TripoliFraction> getTripoliFractions() {
        return tripoliFractions;
    }

    /**
     *
     * @param selection
     * @param visibility
     * @return
     */
    @Override
    public SortedSet<TripoliFraction> getTripoliFractionsFiltered(//
            FractionSelectionTypeEnum selection,//
            IncludedTypeEnum visibility) {

        SortedSet<TripoliFraction> filteredFractions = new TreeSet<>();

        Iterator<TripoliFraction> fractionIterator = tripoliFractions.iterator();
        while (fractionIterator.hasNext()) {
            TripoliFraction tf = fractionIterator.next();
            if ((selection.equals(FractionSelectionTypeEnum.ALL) //
                    || //
                    (selection.equals(FractionSelectionTypeEnum.STANDARD) && tf.isStandard())//
                    || //
                    (selection.equals(FractionSelectionTypeEnum.UNKNOWN) && !tf.isStandard()))//
                    && //
                    visibility.isObjectIncluded(tf.isIncluded())) {

                filteredFractions.add(tf);
                // turn off bad fractions
                if (!tf.confirmHealthyFraction()) {
                    tf.toggleAllDataExceptShaded(false);
                }
            }

        }

        return filteredFractions;
    }

    /**
     *
     * @param selection
     * @param visibility
     * @param tripoliFractionsInput
     * @return
     */
    public static SortedSet<TripoliFraction> getTripoliFractionsFilteredStatic(//
            FractionSelectionTypeEnum selection,//
            IncludedTypeEnum visibility,
            SortedSet<TripoliFraction> tripoliFractionsInput) {

        SortedSet<TripoliFraction> filteredFractions = new TreeSet<>();

        Iterator<TripoliFraction> fractionIterator = tripoliFractionsInput.iterator();
        while (fractionIterator.hasNext()) {
            TripoliFraction tf = fractionIterator.next();
//            if (tf.confirmHealthyFraction()) {
            if ((selection.equals(FractionSelectionTypeEnum.ALL) //
                    || //
                    (selection.equals(FractionSelectionTypeEnum.STANDARD) && tf.isStandard())//
                    || //
                    (selection.equals(FractionSelectionTypeEnum.UNKNOWN) && !tf.isStandard()))//
                    && //
                    visibility.isObjectIncluded(tf.isIncluded())) {

                filteredFractions.add(tf);
                // turn off bad fractions
                if (!tf.confirmHealthyFraction()) {
                    tf.toggleAllDataExceptShaded(false);
                }
            }

        }

        return filteredFractions;

    }

    /**
     * @param tripoliFractions the tripoliFractions to set
     */
    @Override
    public void setTripoliFractions(SortedSet<TripoliFraction> tripoliFractions) {
        this.tripoliFractions = tripoliFractions;
    }

    /**
     *
     * @param fractionSelectionType
     */
    @Override
    public void includeAllFractions(FractionSelectionTypeEnum fractionSelectionType) {
        SortedSet<TripoliFraction> excludedTripoliFractions = //
                getTripoliFractionsFiltered(fractionSelectionType, IncludedTypeEnum.EXCLUDED);
        excludedTripoliFractions.stream().forEach((f) -> {
            f.toggleAllDataExceptShaded(true);
        });
    }

    /**
     *
     */
    @Override
    public void includeAllAquisitions() {
        tripoliFractions.stream().forEach((f) -> {
            f.toggleAllDataExceptShaded(true);
        });
    }

    /**
     *
     */
    @Override
    public void clearAllFractionsOfLocalYAxis() {
        tripoliFractions.stream().forEach((f) -> {
            f.setShowLocalYAxis(false);
        });
    }

    /**
     *
     * @return
     */
    @Override
    public AbstractMassSpecSetup getMassSpec() {
        return rawDataFileHandler.getMassSpec();
    }

    /**
     * @return the downholeFractionationDataModels
     */
    @Override
    public SortedMap<RawRatioNames, DownholeFractionationDataModel> getDownholeFractionationAlphaDataModels() {
        return downholeFractionationDataModels;
    }

    /**
     * @return the sessionForStandardsDownholeFractionation
     */
    private SortedMap<RawRatioNames, AbstractSessionForStandardDataModel> getSessionForStandardsDownholeFractionation() {
        return sessionForStandardsDownholeFractionation;
    }

    /**
     *
     * @return
     */
    @Override
    public SortedMap<RawRatioNames, AbstractSessionForStandardDataModel> getCurrentSessionForStandardsFractionation() {
        SortedMap<RawRatioNames, AbstractSessionForStandardDataModel> currentSessionForStandardsFractionation = null;

        if (fractionationTechnique.compareTo(FractionationTechniquesEnum.DOWNHOLE) == 0) {
            currentSessionForStandardsFractionation = sessionForStandardsDownholeFractionation;
        }

        if (fractionationTechnique.compareTo(FractionationTechniquesEnum.INTERCEPT) == 0) {
            currentSessionForStandardsFractionation = sessionForStandardsInterceptFractionation;
        }

        return currentSessionForStandardsFractionation;
    }

    /**
     * @return the tripoliSamples
     */
    @Override
    public ArrayList<AbstractTripoliSample> getTripoliSamples() {
        return tripoliSamples;
    }

    /**
     * @param tripoliSamples the tripoliSamples to set
     */
    @Override
    public void setTripoliSamples(ArrayList<AbstractTripoliSample> tripoliSamples) {
        this.tripoliSamples = tripoliSamples;
    }

    /**
     * @return the primaryMineralStandard
     */
    @Override
    public AbstractRatiosDataModel getPrimaryMineralStandard() {
        return primaryMineralStandard;
    }

    /**
     * @param primaryMineralStandard the primaryMineralStandard to set
     */
    @Override
    public void setPrimaryMineralStandard(AbstractRatiosDataModel primaryMineralStandard) {
        this.primaryMineralStandard = primaryMineralStandard;
        // dec 2014
        if (tripoliSamples != null) {
            tripoliSamples.get(0).setMineralStandardModel(primaryMineralStandard);
        }
    }

    /**
     * @return the fractionationTechnique
     */
    @Override
    public FractionationTechniquesEnum getFractionationTechnique() {
        return fractionationTechnique;
    }

    /**
     * @param fractionationTechnique the fractionationTechnique to set
     */
    @Override
    public void setFractionationTechnique(FractionationTechniquesEnum fractionationTechnique) {
        this.fractionationTechnique = fractionationTechnique;
    }

    /**
     * @return the timesForPlotting
     */
    @Override
    public double[] getTimesForPlotting() {
        return timesForPlotting;
    }

    /**
     *
     */
    @Override
    public void refreshMaskingArray() {
        MaskingSingleton.setInstance(maskingSingleton);
    }

    /**
     *
     */
    @Override
    public void applyMaskingArray() {
        Iterator<TripoliFraction> fractionIterator = tripoliFractions.iterator();
        while (fractionIterator.hasNext()) {
            TripoliFraction tf = fractionIterator.next();
            tf.applyMaskingArray();
        }
    }

    /**
     *
     */
    @Override
    public void reFitAllFractions() {
        Iterator<TripoliFraction> fractionIterator = tripoliFractions.iterator();
        while (fractionIterator.hasNext()) {
            TripoliFraction tf = fractionIterator.next();
            tf.updateInterceptFitFunctionsIncludingCommonLead();//updateInterceptFitFunctions();
        }
    }

    /**
     *
     * @param setOD
     */
    @Override
    public void setODforAllFractionsAllRatios(boolean setOD) {
        Iterator<TripoliFraction> fractionIterator = tripoliFractions.iterator();
        while (fractionIterator.hasNext()) {
            TripoliFraction tf = fractionIterator.next();
            tf.setODforAllRatios(setOD);
        }
    }

    /**
     * @param estimatedPlottingPointsCount the estimatedPlottingPointsCount to
     * set
     */
    @Override
    public void setEstimatedPlottingPointsCount(int estimatedPlottingPointsCount) {
        this.estimatedPlottingPointsCount = estimatedPlottingPointsCount;
    }

    /**
     * @return the sessionCorrectedUnknownsSummaries
     */
    @Override
    public SortedMap<RadRatios, SessionCorrectedUnknownsSummary> getSessionCorrectedUnknownsSummaries() {
        return sessionCorrectedUnknownsSummaries;
    }

    /**
     * @return the calculatedInitialFitFunctions
     */
    @Override
    public boolean isCalculatedInitialFitFunctions() {
        return calculatedInitialFitFunctions;
    }

    /**
     * @param calculatedInitialFitFunctions the calculatedInitialFitFunctions to
     * set
     */
    public void setCalculatedInitialFitFunctions(boolean calculatedInitialFitFunctions) {
        this.calculatedInitialFitFunctions = calculatedInitialFitFunctions;
    }

    /**
     * @return the maskingSingleton
     */
    @Override
    public MaskingSingleton getMaskingSingleton() {
        return maskingSingleton;
    }

    /**
     *
     * @return
     */
    @Override
    public String getCommonLeadCorrectionHighestLevelFromMasspec() {
        return rawDataFileHandler.getMassSpec().getCommonLeadCorrectionHighestLevel();
    }

    /**
     * @return the dataProcessed
     */
    @Override
    public boolean isDataProcessed() {
        return dataProcessed;
    }

    /**
     * @param dataProcessed the dataProcessed to set
     */
    public void setDataProcessed(boolean dataProcessed) {
        this.dataProcessed = dataProcessed;
    }

    /**
     * @return the leftShadeCount
     */
    public int getLeftShadeCount() {
        return leftShadeCount;
    }

    /**
     * @param leftShadeCount the leftShadeCount to set
     */
    public void setLeftShadeCount(int leftShadeCount) {
        this.leftShadeCount = leftShadeCount;
    }

    /**
     * @return the fitFunctionsUpToDate
     */
    public boolean isFitFunctionsUpToDate() {
        return fitFunctionsUpToDate;
    }

    /**
     * @param fitFunctionsUpToDate the fitFunctionsUpToDate to set
     */
    public void setFitFunctionsUpToDate(boolean fitFunctionsUpToDate) {
        this.fitFunctionsUpToDate = fitFunctionsUpToDate;
    }
}
