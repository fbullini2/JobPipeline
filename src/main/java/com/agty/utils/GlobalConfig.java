package com.agty.utils;

public class GlobalConfig {
    //TODO aggregate here all configs vard
    //see also excel file
    //6hats, ..., ...
    //transform in ENV VARS APP_NAME;
    public static boolean HTTPS_ENABLED=false; //6hats, skills_experts //it should be like the name of the tab in the excel file
    public static String APP_NAME="sixhats"; //sixhats, chatbot, skills_experts, code-helper_java //it should be like the name of the tab in the excel file
    public static boolean AGTs_Answer_Directly_to_Rabbit =false;
    public static boolean HARDWIRED_OVERRIDE_CONFIG =true; // TODO IMPORTANT  RE-EANBLE IT, otherwise it uses only OpenAI !!! <<<<<
    public static boolean LLM_LOCAL_MODE =false;
    public static boolean ANSWERS_SHORTENED =false; //for demo purpose or dev purpose


    public static boolean DEV_MODE =false;
    public static boolean  AGT_AWARE_SYSTEM =true;
    public static boolean DEBUG_SYSTEM_RESOURCE_MODE =false;
    public static boolean DOCKER_CHECKS_MODE =false;//true if docker is needed so the ap should ensure it's well installed etc

    //================================================================================================
    // Functional Configs
    public static String QUESTION_TUPLE_Original3  ="question(Qid,Text,Aid)";//structured
    public static String QUESTION_TUPLE_Vars1stLev  ="question(Qid,Text,Aid,Enabled,Ctx,Goal,SubGoalsList)";//structured
    //rewritten(), constraints(Constraints), worldModel(WorldModel), ts(TS), actionPlan(ActionPlan),style(Style), tags(Tags)
    //TODO add Destination, add group_phase, diff from phhases interne ad un agente, agent interaction protocol, , see muntli phase <<<<<
    // //TODO jakobson funtions; specch act style, contraints, rules, subGoalsList
    //TODO qdd parentAgent, subAgents; add dinamically modifable hirarchy structure in the TC, add delegation mech, re-strucutre of a task or a shqredArtifact like a document or a structure
    //aggiungi input media type   output mediq type , per capire meglio il contesto
    //aggiunig in Destinatario del messaggio , o la sorgente, capire che relazioni abbiamo con lui e precenderi scambi,...e obiettivi...
    public static String QUESTION_TT ="question(Qid,Sender,Text,Aid,enabled(true),ctx(Ctx),goal(Goal,GoalCategory,SubGoalsList), Cost)";
    //structured  Category: goal(GoalDesc,GoalCategory,SubGoalsList)
    public static String QUESTION_TUPLE_Structured_Cost  ="question(Qid,Text,Aid,enabled(true),ctx(Ctx),goal(Goal,GoalCategory,SubGoalsList,Cost)";//structured  Category: goal(GoalDesc,GoalCategory,SubGoalsList)


    public static String ANSWER_TUPLE_Original3  ="answer(Qid,Text,Aid)";
    public static String ANSWER_TUPLE_Adv5  ="answer(Qid,Text,Aid,Time,ModelName,ModelProvider)";//TODO ActualCtxWinSize
    //EstimationInferenceCostPerExchange
    public static String ANSWER_TUPLE_Adv6_Cost  ="answer(Qid,Text,Aid,Time,ModelName,ModelProvider,InferenceCostPerExchange,ActualInferenceCostPerExchange)";//TODO ActualCtxWinSize
    public static String ANSWER_TUPLE_Adv6_Cost_Voting  ="answer(Qid,Text,Aid,Time,ModelName,ModelProvider,InferenceCostPerExchange,ActualInferenceCostPerExchange,AgentsVote)";//TODO ActualCtxWinSize
    public static String ANSWER_TUPLE_Vars9  ="answer(Qid,Text,Aid,Enabled,ModelName,ModelProvider,Time,Tokens,Cost)";//TODO ActualCtxWinSize

//    public static String ANSWER_TT =ANSWER_TUPLE_Adv6_Cost_Voting;
    public static String ANSWER_TUPLE_Adv6_Cost_Voting_subs = "answer(%s,%s,%s,%s,%s,%s,%s,%s,%s)";
    public static String ANSWER_TT8subs =ANSWER_TUPLE_Adv6_Cost_Voting_subs;
    public static String ANSWER_TT =ANSWER_TUPLE_Adv6_Cost;
    //todo: by now a simple var, then will be set to each agent... in diff configs...
    //it should also be dynamic
    public static String MODEL_SELECTION_MODE = "default"; //"bestByCategory", "optionalCost", "bestByPrompt" random, round_robin, cost_based

    //----
    public static String SKILLS_TT ="skill()";
    public static String PRJMNGT_TT ="stratInfo(SName,SPri,func1(Name,Desc)))";
    //---


    // ANSI escape code for red text
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[321m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    // ANSI escape code to reset color
    public static final String ANSI_RESET = "\u001B[0m";
}
