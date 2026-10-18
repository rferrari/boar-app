package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from src/routing/coHazard.test.ts in r4to's PR #85 (BOAR R&D, MIT): every isCoHazardQuestion and coHazardAnswer
 * case. The isCoExposure and conversationCondition cases are left out with the CO card they test (CoHazard's header).
 */
class CoHazardTest {
    @Test fun `fires on a fuel-burning device in an enclosed or attached space`() {
        for (q in listOf(
            "Can I run my portable generator in the attached garage if I keep the door open?",
            "is it ok to use a charcoal grill inside the tent if it's raining",
            "We're camping and it's freezing. Can we bring the propane heater into the camper overnight?",
            "Can I use my Mr. Heater in the basement while I work?",
            "Is it safe to use a camping stove inside the tent to cook?",
            "Power's out. Can I run the generator in the basement with a window cracked?",
            "Can I warm up my car in the garage with the door closed for a few minutes?",
            "Is it safe to use the gas oven to heat the apartment during the outage?",
            "Can I use a disposable bbq in my tent if I leave the flap open?",
            "Can we run a kerosene heater in our ice fishing shack?",
            "I have a generator. Can I keep it running in the shed during the storm?",
            "Can I put the generator in the carport?",
            "Running a pressure washer in an enclosed porch, is that ok?",
        )) assertTrue(q, CoHazard.isCoHazardQuestion(q))
    }

    @Test fun `stays out of symptoms, alarms, electric devices, figurative and non-space mentions`() {
        for (q in listOf(
            "We ran the generator in the garage and now we all have headaches",
            "The CO alarm is beeping in the basement, what do I do?",
            "Can I use an electric heater in the tent?",
            "Can I use my grill in the apartment?",
            "Write a random number generator inside a function in Python",
            "Best charcoal drawing techniques indoors",
            "How many watts does a generator need to run a fridge?",
            "What is a static site generator?",
            "Where can I buy activated charcoal inside the airport?",
            "Is it safe to run a portable generator in the rain?",
            "Garage sale tips: how do I price an old generator?",
            "How do I cook rice on a gas stove?",
            "Is it safe to sleep in a tent in bear country?",
        )) assertFalse(q, CoHazard.isCoHazardQuestion(q))
    }

    @Test fun `reviewer C2, installed or vented appliances and a propane kitchen range do not fire, a vented generator still does`() {
        for (q in listOf(
            "Is it safe to have a gas water heater in the basement?",
            "Is it safe to have a gas water heater in the garage?",
            "Can I use my vented gas heater in the living room?",
            "Is it safe to cook on my propane stove in the kitchen?",
        )) assertFalse(q, CoHazard.isCoHazardQuestion(q))
        assertTrue(CoHazard.isCoHazardQuestion("Can I run the generator in the basement if it's vented?"))
    }

    @Test fun `reviewer C1, never leads with No on risk questions, alternatives or safe setups`() {
        for (q in listOf(
            "Is it safe to run my generator outside 25 ft from the house?",
            "Is it safe to run my generator 6 m from the window?",
            "Can you die from running a generator in the garage?",
            "Can you get CO poisoning from a camping stove in a tent?",
            "Could we get poisoned if we run the generator in the basement?",
            "Is it dangerous to run a generator in the garage?",
            "Is it bad to run a generator in the garage?",
            "Should we move the generator out of the garage?",
            "Should I be concerned about running the generator in the garage overnight?",
            "Should I worry about running the generator in the garage?",
            "Can I keep the generator outside instead of in the garage?",
            "Should I run the generator outside or in the garage?",
            "Can I use my camping stove outside the tent?",
            "Is it ok to move the camping stove out of the tent?",
            "Is it safe to run my generator in the driveway, 25 feet from the garage?",
            "Can I run my generator 30 feet from the garage door?",
            "Is it safe to have a gas water heater in the basement?",
            "Can I use my vented gas heater in the living room?",
            "Is it safe to cook on my propane stove in the kitchen?",
        )) {
            // As shipped: the answer is only shown when the trigger fires; then it must not start with "No.".
            assertFalse(q, CoHazard.isCoHazardQuestion(q) && CoHazard.coHazardAnswer(q).startsWith("No."))
        }
        assertTrue(CoHazard.coHazardAnswer("Can I run my portable generator in the attached garage if I keep the door open?").startsWith("No. Carbon monoxide"))
    }

    @Test fun `follow-up, an installed appliance named in passing no longer vetoes, a kitchen range still does not fire`() {
        for (q in listOf(
            "we're boondocking in an old class C, how do I safely use the propain catalytic heater while we sleep? the furnace eats the battery",
            "can I cook on my butane stove inside the van with the sliding door shut when its raining",
            "doin a crawfish boil and it's stormin, can we run the propane burner inside the shop with the roll up door cracked a couple feet",
            "Can I put a propane heater next to the furnace in the basement?",
        )) assertTrue(q, CoHazard.isCoHazardQuestion(q))
        for (q in listOf("Can I light the propane stove in my RV kitchen?", "Is it safe to use a direct-vent propane heater in the bedroom?"))
            assertFalse(q, CoHazard.isCoHazardQuestion(q))
    }

    @Test fun `symptoms or an alarm with the device context are not the planning answer`() {
        for (q in listOf(
            "Since we started the generator in the garage my head hurts and I feel dizzy and sick. What should I do?",
            "We ran the generator in the garage and now we all have headaches",
            "the alarm next to the propane heater in our camper keeps beeping and my son threw up",
            "been using the charcoal grill in the tent for warmth and now im super dizzy and confused",
        )) assertFalse(q, CoHazard.isCoHazardQuestion(q))
    }

    @Test fun `leads with No on a yes-no planning question only, and ends with sources and the emergency line`() {
        val yesNo = CoHazard.coHazardAnswer("Can I run my generator in the garage?")
        assertTrue(yesNo.startsWith("No. Carbon monoxide"))
        assertTrue(yesNo.contains("Sources: CDC"))
        assertTrue(Regex("(?i)emergency").containsMatchIn(yesNo))
        assertTrue(CoHazard.coHazardAnswer("Where should I put the generator, garage or outside?").startsWith("Carbon monoxide"))
        assertEquals(
            "No. ${CoHazard.CO_HAZARD_EN}\n\n${CoHazard.CO_HAZARD_SOURCES_EN}\n\nIn an emergency, call your local emergency number (911 in the US, 112 in Europe).",
            yesNo,
        )
    }
}
