//  * @param String params.configUrl コンフィグフォルダのPathを指定する。
//  * @param String params.configCredentials "params.configUrl"のフォルダをダウンロードする際に使用するJenkins認証情報。
//  * @param String params.executeLabel 基本として使用する実行ノードラベルの指定。


import java.io.File
import java.nio.file.Files

pipeline {
    agent {
        label "sub_node"
    }

    stages {
        stage('00 init') {
            steps {
                echo "#### 00 init workspace ####"
                script {
                    // workspace削除  
                    echo "${WORKSPACE}"
                    dir("${WORKSPACE}") {
                        deleteDir()
                    }
                }
            }
        }
        

        
        stage('01 Real yaml') {
            steps {
                echo "##### 01 Read yaml #####"
                script {
                    //メッセージ関数の初期化
                    echo '[CI/CT] [Information] 01 config download'
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
                        echo "${env.WORKSPACE}"
                        yamlParentConfig = readYaml file: "${env.WORKSPACE}\\parent.yaml"
                    } catch (Exception e) {
                        echo '[CI/CT] [Information] 02 config analysis fail'
                        currentBuild.result = 'FAILURE'
                        //error
                    }
                }
                echo "[CI/CT] [Information] read finish yamlParentConfig : ${yamlParentConfig}"

            }
        }

        // stage('03 mkdir') {
        //     steps {
        //         script {
        //             echo "##### 03 mkdir #####"
        //             def FolderPath = 'test2'
        //             def FileName = 'text2'
        //             fileOperations([
        //                 folderCreateOperation(folderPath: FolderPath)
        //             ])
        //             fileOperations([
        //                 fileCreateOperation(fileName: "${env.WORKSPACE}\\${FolderPath}\\${FileName}.txt", fileContent: "test context")
        //             ])
        //         }
        //     }
        // }

        stage('Clone Git PushSaki') {
            steps {
                script {
                    echo "Clone Git PushSaki"
                    // ソースコードのリモートリポジトリをローカルリポジトリにダウンロード
                    dir(path: "${env.WORKSPACE}\\PushSaki"){
                    try {
                        checkout([$class: 'GitSCM', 
                                    branches: [[name: "${params.configBranches}"]], 
                                    doGenerateSubmoduleConfigurations: false, 
                                    extensions: [
                                        [$class: 'SparseCheckoutPaths',  sparseCheckoutPaths:[[$class:'SparseCheckoutPath', path:'System_X/']]]
                                                ],
                                    submoduleCfg: [],
                                    userRemoteConfigs: [
                                        [credentialsId: "${params.configCredentials}", 
                                        url: "${params.PushSakiUrl}"
                                        ]
                                    ]
                                ])
                        } catch (Exception  e) {
                            printMessage(e)
                            currentBuild.result = 'FAILURE'
                        }
                    }
                }
            }
        }



        // stage('04 copy result') {
        //     steps {
        //         script {
        //             echo "##### 04 copy result #####"
        //             echo "${env.WORKSPACE}"
        //             fileOperations([folderCopyOperation(sourceFolderPath:"${env.WORKSPACE}\\test2\\", 
        //                             destinationFolderPath:"${env.WORKSPACE}\\PushSaki")])
        //         }
        //     }
        // }

        // stage ('Push Git'){
        //     steps{
        //         script{
        //             echo "Push Git"
        //             echo "${env.JOB_NAME}"
        //             dir(path: "${env.WORKSPACE}\\PushSaki"){
        //                 try {
        //                     bat """
        //                         git checkout main
        //                         git add .
        //                         git commit -am \"ATGCode Push by Jenkins\"
        //                         set GIT_TRACE=1
        //                         git push origin main
        //                         """
        //                 } catch (Exception  e) {
        //                     printMessage(e)
        //                     currentBuild.result = 'FAILURE'
        //                 }
        //             }
        //         }
        //     }
        // }

    }

    post {
        always {
            script {
                //現在仮実装
                // variable echo check
                // ScopeID の詳細欄への表示
                currentBuild.description = "ScopeID:"

                echo "${currentBuild.currentResult}"
                echo "${currentBuild.durationString}"
                echo "${currentBuild.description}"
                echo "${currentBuild.absoluteUrl}"
                echo "${yamlParentConfig.common.mailOfTester}"
                echo "${BUILD_URL}"

                emailext body: """${JOB_NAME} is ${currentBuild.currentResult}
                                time : ${currentBuild.durationString}
                                details : ${currentBuild.description}
                                Look more details : ${currentBuild.absoluteUrl}""",
                         subject: "Jenkinsbuild ${JOB_NAME}:${BUILD_ID}",
                         to: "${yamlParentConfig.common.mailOfTester}"

            }
        }
    }


        

}

