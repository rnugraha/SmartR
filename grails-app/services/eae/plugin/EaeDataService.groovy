package eae.plugin

import grails.util.Environment
import grails.util.Holders
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import smartR.plugin.DataQueryService

class EaeDataService {

    def DEBUG = Environment.current == Environment.DEVELOPMENT
    def DEBUG_TMP_DIR = '/tmp/'

    def grailsApplication = Holders.grailsApplication
    def springSecurityService
    def i2b2HelperService
    def dataQueryService


    def queryData(params) {

        def parameterMap = createParameterMap(params)
        def data_cohort1 = [:]
        def data_cohort2 = [:]

        def rIID1 = parameterMap['result_instance_id1'].toString()
        def rIID2 = parameterMap['result_instance_id2'].toString()

        def patientIDs_cohort1 = rIID1 ? i2b2HelperService.getSubjectsAsList(rIID1).collect { it.toLong() } : []
        def patientIDs_cohort2 = rIID2 ? i2b2HelperService.getSubjectsAsList(rIID2).collect { it.toLong() } : []

        parameterMap['conceptBoxes'].each { conceptBox ->
            conceptBox.cohorts.each { cohort ->
                def rIID
                def data
                def patientIDs

                if (cohort == 1) {
                    rIID = rIID1
                    patientIDs = patientIDs_cohort1
                    data = data_cohort1
                } else {
                    rIID = rIID2
                    patientIDs = patientIDs_cohort2
                    data = data_cohort2
                }

                if (! rIID || ! patientIDs) {
                    return
                }

                if (conceptBox.concepts.size() == 0) {
                    data[conceptBox.name] = [:]
                } else if (conceptBox.type == 'valueicon' || conceptBox.type == 'alphaicon') {
                    data[conceptBox.name] = dataQueryService.getAllData(conceptBox.concepts, patientIDs)
                } else if (conceptBox.type == 'hleaficon') {
                    def rawData = dataQueryService.exportHighDimData(
                            conceptBox.concepts,
                            patientIDs,
                            rIID as Long)
                    data[conceptBox.name] = rawData
                } else {
                    throw new IllegalArgumentException()
                }
            }
        }

        parameterMap['data_cohort1'] = new JsonBuilder(data_cohort1).toString()
        parameterMap['data_cohort2'] = new JsonBuilder(data_cohort2).toString()

        if (DEBUG) {
            new File(DEBUG_TMP_DIR + 'data1.json').write(parameterMap['data_cohort1'])
            new File(DEBUG_TMP_DIR + 'data2.json').write(parameterMap['data_cohort2'])
        }

        return parameterMap
    }


    def createParameterMap(params){
        def parameterMap = [:]
        parameterMap['result_instance_id1'] = params.result_instance_id1
        parameterMap['result_instance_id2'] = params.result_instance_id2
        parameterMap['conceptBoxes'] = new JsonSlurper().parseText(params.conceptBoxes)
        parameterMap['DEBUG'] = DEBUG
        return parameterMap
    }

    def buildMongoQuery(params){
        def result_instance_id1 = params.result_instance_id1;
        def result_instance_id2 = params.result_instance_id2;
        def highDimParam = new JsonSlurper().parseText(params.conceptBoxes);


        return parameterMap
    }

    def  SendToHDFS (String username, String mongoDocumentID, String genesList, String scriptDir, String sparkURL) {
        def script = scriptDir +'transferToHDFS.sh'
        def fileToTransfer = "geneList-" + username + "-" + mongoDocumentID + ".txt"

        def scriptFile = new File(script)
        if (scriptFile.exists()) {
            if(!scriptFile.canExecute()){
                scriptFile.setExecutable(true)
            }
        }else {
            log.error('The Script file to transfer to HDFS wasn\'t found')
        }

        File f =new File("/tmp/eae/",fileToTransfer)
        if(f.exists()){
            f.delete()
        }
        f.withWriter('utf-8') { writer ->
            writer.writeLine genesList
        } // or << genesList
        f.createNewFile()

        String fp = f.getAbsolutePath()
        def executeCommand = script + " " + fp + " "  + fileToTransfer + " " + sparkURL
        executeCommand.execute().waitFor()

        // We cleanup
        f.delete()

        return 0
    }


}
