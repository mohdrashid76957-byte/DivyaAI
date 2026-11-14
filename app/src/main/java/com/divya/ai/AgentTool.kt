package com.divya.ai

/**
 * v3.16: Gemini tool "agent_task". Agent engine ka state yahan se control hota hai.
 * action: begin (goal ke saath plan banao) | status (kya chal raha hai) | cancel (task rok do).
 * Screen par actions Divya ke purane tools (tap_text, type_text, do_steps...) se hi hote hain;
 * ye tool sirf plan, progress aur cancel sambhalta hai.
 */
object AgentTool {
    fun run(action: String, goal: String): String = when (action.lowercase()) {
        "begin", "start" -> {
            if (goal.isBlank()) "Task ka goal batao."
            else {
                val c = AgentTaskEngine.begin(goal)
                "Task shuru: ${c.originalGoal}. Plan: ${c.plan.joinToString(" -> ")}. Har step ke baad status check karo."
            }
        }
        "status" -> AgentTaskEngine.current()?.let { c ->
            "Task: ${c.originalGoal}. Status: ${c.status}. Current step: ${c.currentStep ?: "-"}. " +
                "Done: ${c.completedSteps.size}, baaki: ${c.pendingSteps.size}. " +
                "Last action: ${c.lastAction ?: "-"} (${if (c.lastActionVerified) "verified" else "not verified"})."
        } ?: "Koi task chal nahi raha."
        "cancel", "stop" -> {
            if (AgentTaskEngine.current() == null) "Koi task chal nahi raha."
            else { AgentTaskEngine.requestCancellation(); "Task rokne ka signal diya." }
        }
        else -> "action begin, status ya cancel hona chahiye."
    }
}
