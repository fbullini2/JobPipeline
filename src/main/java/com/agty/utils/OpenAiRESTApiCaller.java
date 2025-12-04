package com.agty.utils;


import com.agty.ApiKeys;
import dev.ai4j.openai4j.OpenAiHttpException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.time.Duration.ofSeconds;
import static com.agty.utils.GlobalConfig.*;
import static com.agty.utils.TokenEstimator.countNonWhitespaceCharacters;

public class OpenAiRESTApiCaller {

    public static Logger logger = LoggerFactory.getLogger(OpenAiRESTApiCaller.class);

    /**
     * Call OpenAI API and return response with usage tracking and cost calculation
     */
    public static LLMUsageInfo callerWithUsage(String Aid, String modelName, String systemPrompt, String userPrompt, String llmProviderTool, Double temperature, Integer maxOutputTokens) {
        return callerInternal(Aid, modelName, systemPrompt, userPrompt, llmProviderTool, temperature, maxOutputTokens, true);
    }

    /**
     * Call OpenAI API and return only the response text (backwards compatible)
     */
    public static String caller(String Aid, String modelName, String systemPrompt, String userPrompt, String llmProviderTool, Double temperature, Integer maxOutputTokens) {
        LLMUsageInfo usageInfo = callerInternal(Aid, modelName, systemPrompt, userPrompt, llmProviderTool, temperature, maxOutputTokens, false);
        return usageInfo.getResponse();
    }

    /**
     * Internal method that does the actual API call
     */
    private static LLMUsageInfo callerInternal(String Aid, String modelName, String systemPrompt, String userPrompt, String llmProviderTool, Double temperature, Integer maxOutputTokens, boolean trackCost) {
        //TODO VERY IMPORTANT REFACTORY: this code is similar to the request in teh Agent PArticipantToTheDebate...
        //Tokenizer tokenizer = new OpenAiTokenizer("gpt-4o");
        /*
         * /**
         * This class is deprecated. Use one of the following enums instead:
         * <pre>
         * {@link OpenAiChatModelName}
         * {@link OpenAiEmbeddingModelName}
         * {@link OpenAiImageModelName}
         * {@link OpenAiLanguageModelName}
         * {@link OpenAiModerationModelName}
         * </pre>
         */
        ArrayList<OpenAiChatModelName> values = new ArrayList<>(Arrays.asList(OpenAiChatModelName.values()));
        if (modelName == null || modelName.isEmpty()) {
            String fallbackModel=OpenAiChatModelName.GPT_4_O_MINI.toString();
//                    String fallbackModel=OpenAiChatModelName.GPT_3_5_TURBO.toString();
            logger.debug(Aid + " requested OpenAI ModelName=" + modelName + ", setting fallback model=" + fallbackModel);
            modelName = fallbackModel;
        } else {
//                    OpenAiChatModelName v = OpenAiChatModelName.valueOf(ModelName.trim());
//                    System.out.println(values.contains(v));
//                    System.out.println("ModelName="+ModelName);
//                    System.out.println(values);
//                    System.out.println(values.contains(ModelName));
            boolean found = false;
            for (OpenAiChatModelName v : values) {
                if (v.toString().equals(modelName)) {
                    logger.info(Aid + " requested OpenAI ModelName=" + modelName + " found in enum=" + v);
                    found = true;
                    break;
                }
            }
            if (!found) {
                logger.info(Aid + " requested OpenAI ModelName=" + modelName + " not found in enum "+ OpenAiChatModelName.class.getName()+" , setting " + OpenAiChatModelName.GPT_3_5_TURBO);
                modelName = OpenAiChatModelName.GPT_3_5_TURBO.toString();
            }
        }
        String apiKey = null;
        if (modelName.startsWith("gpt-3.5")) {
            apiKey = "demo";
        } else {
            apiKey = ApiKeys.OPENAI_API_KEY;
        }
//                if (DEV_MODE) {
//                    completePrompt="Provide a minimal answer to teh question: "+completePrompt;
//                }
        OpenAiChatModel model = OpenAiChatModel.builder()
                //.baseUrl()
                .apiKey(apiKey) //ApiKeys.OPENAI_API_KEY // "demo" for gpt-3.5-turbo <<< still good for certain tasks, or agentic...
                //.organizationId()    //TODO: <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
                .modelName(modelName)
                .temperature(temperature) //TODO allow these values change at runtime (AtomicLock ...)
//                        .topP()
//                        .stop())
                .maxTokens(maxOutputTokens)   //TODO: <<<<<<<<<<<<<<<<<<<<<<<<<<< MAx toekn nella rispsota é paramtro di ottimizz costi
//                        .presencePenalty()
//                        .frequencyPenalty()
//                        .logitBias() //see course ollama youtube ...
//                        .responseFormat() //TODO: <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
//                        .seed()
//                        .user()    //TODO: <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                //.tokenizer()
//                        .maxRetries(chatModelProperties.getMaxRetries())
//                        .proxy(ProxyProperties.convert(chatModelProperties.getProxy()))
                .build();
        //todo use  List<AiMessges><-generate(messages...)
        // Generates a response from the model based on a sequence of messages. Typically, the sequence contains messages in the following order: System (optional) - User - AI - User - AI - User ...
        //TODO-Important: make it generci for Goal, Context, Theme,
        //GENERALUZE ESTIMATION OF TOKENS, COST etc:
        int estimUserToeknCount = model.estimateTokenCount(userPrompt);//MATCH Better estiamtion...use this
        int estimSystemTokenCount = 0;
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            estimSystemTokenCount = model.estimateTokenCount(systemPrompt);
        } else {
            System.out.println(Aid + " System Prompt empty; ");
        }
        System.out.println(Aid + " estimated USER prompt ToeknCount from OpenAI estimator (same as model requested, so more accurate)=" + estimUserToeknCount);
        System.out.println(Aid + " estimated SYSTEM prompt TokenCount from OpenAI estimator (same as model requested, so more accurate)=" + estimSystemTokenCount);
        UserMessage um = new UserMessage(userPrompt);
        List<ChatMessage> cms = new ArrayList<>();
        cms.add(um);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            if (DEV_MODE) {
                int limitInTokens = 1000; //open AI free-demo limit
                if (estimSystemTokenCount+estimUserToeknCount >= limitInTokens) {
                    int charsSystemPrompt=countNonWhitespaceCharacters(systemPrompt);
                    int charsUserPrompt=countNonWhitespaceCharacters(userPrompt);
                    int charsPerTokenS = charsSystemPrompt / estimSystemTokenCount;//int... rounded down
                    int charsPerTokenU = charsUserPrompt / estimUserToeknCount;//int... rounded down
                    int charPerToken = (charsPerTokenS+charsPerTokenU)/2;
                    int limitInChars = charPerToken*(estimSystemTokenCount+estimUserToeknCount);
                    System.out.println(Aid+" systemPrompt length="+charsSystemPrompt+ " tokenCount="+estimSystemTokenCount);
                    System.out.println(Aid+" userPrompt length="+charsUserPrompt+ " tokenCount="+estimUserToeknCount);
                    System.out.println(Aid + " completePrompt too long, it will be cut to 1000 tokens");
                    systemPrompt = systemPrompt.substring(0, limitInChars - charsUserPrompt);
                    System.out.println(Aid+" new systemPrompt length="+systemPrompt.length());
//                            System.out.println(Aid+" new userPrompt length="+userPrompt.length());
                }
                System.out.println(Aid + " estimated SYSTEM prompt TokenCount from OpenAI estimator (same as model requested, so more accurate)=" + estimSystemTokenCount);
            }
            SystemMessage sm = new SystemMessage(systemPrompt);
            cms.add(sm);
        } else {
            System.out.println(Aid + " System Prompt empty; it can happen only if the request come from a 'system'-method not known to teh user; e.g. find themes, or extracting goals...");
        }
        //TODO AiMessage(s), responses from AI... previous ones the ones from teh current turn, from AI
        //TODO VERY-IMPORTANT: AiMessage  CAN TAKE INTO ACCOUNT MESSAGE FROM OTHER ASSISTANTS AI <<< !!!!
        Response<AiMessage> response = null;
        String responseAnswerSynchStr;
        try {
            //if (DEV_MODE) {
//                        String responseStrTempDev = model.generate(userPrompt+" "+systemPrompt);
//                        System.out.println("response check DEV MODE from OpenAI API from pure completePrompt=" + responseStrTempDev);
//                    }
            long start = System.currentTimeMillis();

            response = model.generate(cms);
            //TODO HANDLE THE CASE...
            //Only gpt-4o-mini model is available for demonstration purposes. If you wish to use another model, please use your own OpenAI API key.
            if (response == null) {
                System.err.println(ANSI_RED+ Aid + " tool=" + llmProviderTool +" model="+model +" response from OpenAI API is NULL for question cms"+cms+ANSI_RESET);
                responseAnswerSynchStr = "I apologize, I don't have an answer for you at the moment.";
            }else {
                if (response.content().text().contains("model is available for demonstration purposes. If you wish to use another model, please use your own OpenAI API key")) {
                    System.err.println(ANSI_RED+ Aid + " tool=" + llmProviderTool +" model="+model +" response from OpenAI API is NULL for question cms"+cms+ANSI_RESET);
                    responseAnswerSynchStr = "I apologize, I don't have an answer for you at the moment.";
                }}
            long end = System.currentTimeMillis();
            System.out.println("response from OpenAI API=" + response);
            responseAnswerSynchStr = response.content().text();
            long time = end - start;
            System.out.println(Aid +" "+ OpenAiRESTApiCaller.class.getName()+ " Time to answer: " + (double) (time / 1000) + " sec = " + ((double) (time / 1000) / 60) + " min");
            System.out.println(Aid +" "+ OpenAiRESTApiCaller.class.getName()+ ": model used=" + modelName + " used via=" + llmProviderTool);

            Integer actualInputTokens = response.tokenUsage().inputTokenCount();
            Integer actualOutputTokens = response.tokenUsage().outputTokenCount();
            Integer totTokens = response.tokenUsage().totalTokenCount();
            System.out.println(Aid +" "+ OpenAiRESTApiCaller.class.getName()+"======> ACTUAL token usage=" + actualInputTokens + " input tokens," + actualOutputTokens + " output tokens, " + totTokens + " total tokens");

            // Calculate cost if tracking is enabled
            if (trackCost) {
                double cost = LLMCostCalculator.calculateCost(modelName, actualInputTokens, actualOutputTokens);
                System.out.println(Aid +" "+ OpenAiRESTApiCaller.class.getName()+"======> COST: " + LLMCostCalculator.formatCost(cost) + " USD");
                return new LLMUsageInfo(responseAnswerSynchStr, actualInputTokens, actualOutputTokens, totTokens, cost, modelName);
            }

        } catch (OpenAiHttpException e) {
            System.err.println("OpenAI HTTP Exception: " + e.getMessage());
            responseAnswerSynchStr = "I apologize, I don't have an answer for you at the moment.";
            return new LLMUsageInfo(responseAnswerSynchStr);
            //TODO Possibily redirect on an EQUIVALENT model, or if possible the same model on a different provider
            // In theory this shouldn t happen because it s typiczzly QUOTA ISSUES OR API KEY EXPIRED...
        } catch (Exception e) {
            System.err.println(OpenAiRESTApiCaller.class.getName()+" OpenAI Exception: " + e.getMessage());
            e.printStackTrace();
            responseAnswerSynchStr = "I apologize, I don't have an answer for you at the moment.";//TODO unavailable, the text will be added later form the outgoing agent
            return new LLMUsageInfo(responseAnswerSynchStr);
            //TODO Possibily redirect on an EQUIVALENT model, or if possible the same model on a different provider
            // In theory this shouldn t happen because it s typiczzly QUOTA ISSUES OR API KEY EXPIRED...
        }
        //message = completion.choices[0].message.content
                    /*https://platform.openai.com/docs/guides/chat-completions/response-format?lang=curl
                    * Every response will include a finish_reason. The possible values for finish_reason are:
                    stop: API returned complete message, or a message terminated by one of the stop sequences provided via the stop parameter
                    length: Incomplete model output due to max_tokens parameter or token limit
                    function_call: The model decided to call a function
                    content_filter: Omitted content due to a flag from our content filters
                    null: API response still in progress or incomplete
                    Depending on input parameters, the model response may include different information.
                    * */

        // handle this possible message: Only 'gpt-3.5-turbo' model is available for demonstration purposes. If you wish to use another model, please use your own OpenAI API key
        if (responseAnswerSynchStr == null || responseAnswerSynchStr.isEmpty() || responseAnswerSynchStr.equals(".") || responseAnswerSynchStr.equals("...")) {
            System.err.println(Aid + " " + llmProviderTool + " OPEN AI API ERROR response =" + responseAnswerSynchStr + "; so I set circumstantial default answer");
            responseAnswerSynchStr = "I apologize, I don't have an answer for you at the moment.";
            // TODO EmailSender.sendEmail("OpenAI API ERROR", "", "OpenAI API ERROR response " + new Date(), "ERROR response text= " + response);
            // prepare an ACTION and TOOL // initially just a lib// then a Docker of apahce james
        }

        //TODO see OpenAI API!!!!
        //TODO in case the answer is just a dot !!! ".";  CHECK IF THE ANSWER IS EMPTY OR JUST A DOT BECAUSE IT IS NOT WORKING
        // OR SOME THRESHOOLD OR LIMIT HAS BEEN REACHED
        //OR ENDPOINT CHANGED OR API KEY EXPIRED
        //Response<AiMessage> generate(List<ChatMessage> messages); RESP FROM A LIST OF MESSAGES!!! <<<<
        //Response<AiMessage> generate(ChatMessage message, AND a LIST OF TOOLS !!); RESP FROM A SINGLE MESSAGE <<<

        // Return LLMUsageInfo with response string for backwards compatibility
        return new LLMUsageInfo(responseAnswerSynchStr);
    }

}
//some tech notes:
//https://platform.openai.com/docs/guides/chat-completions/response-format?lang=curl
                /* TODO VIMPORTANT: OpenAPI ALLOW TO DISCRIMINATE ROLES IN THE TEXT SEE BELOW;
                * curl https://api.openai.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "model": "gpt-4o-mini",
    "messages": [
      {
        "role": "system",
        "content": "You are a helpful assistant."
      },
      {
        "role": "user",
        "content": "Who won the world series in 2020?"
      },
      {
        "role": "assistant",
        "content": "The Los Angeles Dodgers won the World Series in 2020."
      },
      {
        "role": "user",
        "content": "Where was it played?"
      }
    ]
  }'
  *
  * EXAMPLE OUTPUT:
  * {
  "choices": [
    {
      "finish_reason": "stop",
      "index": 0,
      "message": {
        "content": "The 2020 World Series was played in Texas at Globe Life Field in Arlington.",
        "role": "assistant"
      },
      "logprobs": null
    }
  ],
  "created": 1677664795,
  "id": "chatcmpl-7QyqpwdfhqwajicIEznoc6Q47XAyW",
  "model": "gpt-4o-mini",
  "object": "chat.completion",
  "usage": {
    "completion_tokens": 17,
    "prompt_tokens": 57,
    "total_tokens": 74
  }
}
                * */