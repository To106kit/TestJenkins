/**
 * CI/CTシステムにおける、Jenkins上のJobを管理する
 * 
 * @version 1.04.00
 * 
 * @param String params.configUrl コンフィグフォルダのPathを指定する。
 * @param String params.configCredentials "params.configUrl"のフォルダをダウンロードする際に使用するJenkins認証情報。
 * @param String params.executeLabel 基本として使用する実行ノードラベルの指定。
 * 
 * @return currentBuildクラスオブジェクト(RunWrapper)
 */

//ライブラリの読み込み
library 'SPIFWLibrary@R2021.B.00'

//Map&List型 "parent.yaml"の内容を取得する変数
def yamlParentConfig
//Map&List型 "yamlParentConfig"から実行するものだけを取得する変数
def activeChiidList
//”getFolderName”関数より取得した文字列保存用変数
String jobFolderName = getFolderName()


pipeline {
    agent {
        label "${params.executeLabel}"
    }
    stages {
        stage('Init') {
            steps {
                script {
                    //メッセージ関数の初期化
                    initialMessage()
                    
                    echo '[CI/CT] [Information] 01 config download'
                    //Configフォルダを指定Pathよりダウンロード
                    try {
                        if(params.configBranches == null){
                            checkout([$class: 'SubversionSCM',
                                additionalCredentials: [],
                                excludedCommitMessages: '',
                                excludedRegions: '',
                                excludedRevprop: '',
                                excludedUsers: '',
                                filterChangelog: false,
                                ignoreDirPropChanges: false,
                                includedRegions: '',
                                locations: [
                                        [cancelProcessOnExternalsFail: true,
                                            credentialsId: "${params.configCredentials}",
                                            depthOption: 'infinity',
                                            ignoreExternalsOption: true,
                                            local: '.',
                                            remote: "${params.configUrl}"]
                                ],
                                quietOperation: true, workspaceUpdater: [$class: 'UpdateUpdater']]
                            )
                        }else{
                            checkout([$class: 'GitSCM', 
                                branches: [[name: "${params.configBranches}"]], 
                                doGenerateSubmoduleConfigurations: false, 
                                extensions: [], 
                                submoduleCfg: [], 
                                userRemoteConfigs: [
                                    [credentialsId: "${params.configCredentials}", 
                                    url: "${params.configUrl}"
                                    ]
                                ]
                            ])
                        }
                    } catch (Exception e) {
                        printMessage("0001000001","${params.configUrl}")
                        currentBuild.result = 'FAILURE'
                        //error
                    }
                }
                
                echo '[CI/CT] [Information] 02 config analysis'
                //01でダウンロードしたConfigフォルダ内の"parent.yaml"を"yamlParentConfig"へ読み込む。
                script{
                    try {
                        yamlParentConfig = readYaml file: "${env.WORKSPACE}\\parent.yaml"
                    } catch (Exception e) {
                        printMessage("0001000002")
                        currentBuild.result = 'FAILURE'
                        //error
                    }
                }
                echo "[CI/CT] [Information] read finish yamlParentConfig : ${yamlParentConfig}"
                
                // ScopeID の詳細欄への表示
                script{
                    currentBuild.description = "ScopeID:${yamlParentConfig.scopeID}"
                }

                echo '[CI/CT] [Information] 03 config check and preparation'
                script{
                    //PreparationConfigの確認
                    String netDriveResultSpace = "${yamlParentConfig.common.containerUrl}\\${jobFolderName}\\${env.BUILD_ID}\\Result"
                    if (!fileExists("${env.WORKSPACE}\\Preparation")) {
                        printMessage("0001000003")
                        currentBuild.result = 'FAILURE'
                        //error
                    }
                    for(tool in yamlParentConfig.setTools){
                        //Map&List型 "yamlParentConfig"内の中でToolごとに取得した後の、再定義用変数
                        def toolTmp = tool
                        //"yamlParentConfig.processName[]"からflg=trueのものだけをフィルター処理し、"activeChiidList"へ代入する。
                        activeChiidList = toolTmp.info.findAll{it.flg == true}
                        echo "[CI/CT] [Information] 実行ステップ：${toolTmp.processName} = ${activeChiidList}"
                        
                        //実行ツールリストの名前を取得し、Configフォルダ内の存在チェックとResultフォルダの作成を行う。
                        for(checkChild in activeChiidList.name){
                            String checkChildTmp = checkChild as String
                            if (!fileExists("${env.WORKSPACE}\\${toolTmp.processName}\\${checkChildTmp}")) {
                                printMessage("0001000004",toolTmp.processName,checkChildTmp)
                                currentBuild.result = 'FAILURE'
                                //error
                            }
                            // try 
                            try{
                            bat '''
                                net use Z: https://webdav.geniie.net/gsp4inci/gsp4inci/ Tto106kI /u:takuma.todoroki.j6x@jpgr.denso.com  /persistent:yes
                            '''
                            }catch (Exception e){
                                //設定済みのため何もしない.
                            }
                            fileOperations([
                                folderCreateOperation(folderPath: "${netDriveResultSpace}\\${toolTmp.processName}\\${checkChildTmp}")
                            ])
                            echo "[CI/CT] [Information] create ${netDriveResultSpace}\\${toolTmp.processName}\\${checkChildTmp}"
                        }
                    }
                }
                
                //Configフォルダをネットドライブへアップロード
                script {
                    try{
                        fileOperations([
                            folderCopyOperation(sourceFolderPath: "${env.WORKSPACE}",
                                                destinationFolderPath: "${yamlParentConfig.common.containerUrl}\\${jobFolderName}\\${env.BUILD_ID}\\Config")
                        ])
                    }catch (Exception e){
                        printMessage("0001000005")
                        currentBuild.result = 'FAILURE'
                        //error
                    }
                }
                
                echo '[CI/CT] [Information] 04 Create Vari file'
                //"yamlParentConfig"からVARI情報のみを取得し、子ジョブへ引き継ぐためのVARI情報のyamlファイルとして生成し、netDriveへコピーする。
                script {
                    if(yamlParentConfig.vari != null){
                        writeYaml file: 'vari.yaml', data: yamlParentConfig.vari
                        fileOperations([
                            fileCopyOperation(excludes: '', flattenFiles: true, includes: 'vari.yaml',
                                            targetLocation: "${yamlParentConfig.common.containerUrl}\\${jobFolderName}\\${env.BUILD_ID}"),
                            fileDeleteOperation(excludes: '', includes: 'vari.yaml')
                        ])
                    }
                }
                
            }
        }
        stage('Preparation') {
            steps {
                echo '[CI/CT] [Information] 01 Execute a Preparation job'
                //PreparationJobの実行
                build job: 'Preparation',
                    parameters: [
                        string(name: 'buildId', value: "${env.BUILD_ID}"),
                        string(name: 'netDrive', value: "${yamlParentConfig.common.containerUrl}\\${jobFolderName}"),
                        string(name: 'netDriveUsespace', value: "${yamlParentConfig.common.containerUrl}\\${jobFolderName}\\${env.BUILD_ID}"),
                        string(name: 'executeLabel', value: params.executeLabel),
                        string(name: 'scopeID', value: "${yamlParentConfig.scopeID}")
                    ], wait: true
            }
        }
        stage('post ChildJob') {
            steps {
                script {
                    
                    echo '[CI/CT] [Information] 01 Execute a child jobs'
                    currentBuild.result = "SUCCESS"
                    //子ジョブの実行をすべて行う。
                    for(tool in yamlParentConfig.setTools){
                        def toolTmp = tool
                        //activeChiidListの再取得（保持していないため。）
                        activeChiidList = toolTmp.info.findAll{it.flg == true}

                        //"activeChiidList"より、名前を取得し、取得した名前でツールを判別し、実行する。
                        for(activeChild in activeChiidList.name) {

                            if(!currentBuild.result.equals("FAILURE")){
                                String activeChildTmp = activeChild as String
                                echo "[CI/CT] [Information] Execute a ${activeChildTmp} job"
                                String customContainerUrl = "${yamlParentConfig.common.containerUrl}\\${jobFolderName}"
                                String jobresult = buildJob(toolTmp.processName, activeChildTmp, customContainerUrl, "${yamlParentConfig.scopeID}")
                                if(jobresult.equals("ABORTED")){
                                    currentBuild.result = "FAILURE"
                                }else if(!jobresult.equals("SUCCESS")){
                                    // SUCCESS -> UNSTABLE -> SUCCESSを考慮した条件文
                                    currentBuild.result = jobresult
                                }
                            }
                        }
                    }
                }
            }
        }
        stage('Delete workspace') {
            steps {
                echo '[CI/CT] [Information] 01 delete folder'
                //ワークスペース内のデータを削除する。
                script {
                    def count = 0;
                    while(count < 5) {
                        try {
                            dir(path: "${env.WORKSPACE}"){deleteDir()}
                            if(!fileExists("${env.WORKSPACE}")){
                                break
                            }
                        }catch (Exception e) {
                            printMessage("1001000001")
                            count = count + 1
                            sleep 5
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                //現在仮実装
                emailext body: """${JOB_NAME} is ${currentBuild.currentResult}
                                time : ${currentBuild.durationString}
                                details : ${currentBuild.description}
                                Look more details : ${currentBuild.absoluteUrl}""",
                         //recipientProviders: [requestor(), upstreamDevelopers()],
                         subject: "Jenkinsbuild ${JOB_NAME}:${BUILD_ID}",
                         to: "${yamlParentConfig.common.mailOfTester}"

            }
        }
    }
}

/**
 * 実行Jobのプロジェクト名を取得する。//任意の名前を取得。JenkinsJobのフォルダ階層がProject名/Parentになるため、Project名を取得。
 * 
 * @version 1.0
 * 
 * @param なし
 * 
 * @return Jobプロジェクト名の返却
 */
String getFolderName (){
    String sJobName = "${JOB_NAME}"
    String sJobBaseName = "${JOB_BASE_NAME}"
    return sJobName.getAt(0..((sJobName.size()-1) - (sJobBaseName.size()+1)))
}

/**
 * 子ジョブの呼び出しを行う関数。//yamlPropertyConfigの値がGrobalにしているはずだが引き継げないため、引数として渡す。
 * 
 * @version 1.1
 * 
 * @param String step 実行Jobのステップ名管理用変数
 * @param String jobname 実行Job名管理用変数
 * @param String customContainerUrl 使用するネットドライブフォルダ管理用変数
 * 
 * @return JOB実行結果
 */
String buildJob (String step, String jobname, String customContainerUrl, String scopeID){
    def jobresult = build job: "${step}/${jobname}",
    parameters: [
        string(name: 'netDrive', value: "${customContainerUrl}"),
        string(name: 'netDriveUsespace', value: "${customContainerUrl}\\${env.BUILD_ID}"),
        string(name: 'jobFolderPath', value: "${step}\\${jobname}"),
        string(name: 'jobName', value: jobname),
        string(name: 'executeLabel', value: params.executeLabel),
        string(name: 'scopeID', value: "${scopeID}")
    ], wait: true, propagate: false
    return jobresult.result
}