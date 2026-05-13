package com.androidplay.weatherify.di

import com.androidplay.core.mongo.IndexSpec
import com.androidplay.weatherify.db.WeatherifyDb
import com.androidplay.weatherify.repository.FeedbackRepository
import com.androidplay.weatherify.repository.NoteRepository
import com.androidplay.weatherify.repository.PaymentRepository
import com.androidplay.weatherify.repository.RefundRepository
import com.androidplay.weatherify.repository.SavedLocationRepository
import com.androidplay.weatherify.repository.ServiceCatalogRepository
import com.androidplay.weatherify.repository.UserRepository
import com.androidplay.weatherify.repository.mongo.FeedbackRepositoryImpl
import com.androidplay.weatherify.repository.mongo.NoteRepositoryImpl
import com.androidplay.weatherify.repository.mongo.PaymentRepositoryImpl
import com.androidplay.weatherify.repository.mongo.RefundRepositoryImpl
import com.androidplay.weatherify.repository.mongo.SavedLocationRepositoryImpl
import com.androidplay.weatherify.repository.mongo.ServiceCatalogRepositoryImpl
import com.androidplay.weatherify.repository.mongo.UserRepositoryImpl
import com.mongodb.client.model.IndexOptions
import org.bson.Document
import org.koin.dsl.module

fun weatherifyModule() = module {
    single { WeatherifyDb(get()) }

    single<UserRepository> { UserRepositoryImpl(get()) }
    single<FeedbackRepository> { FeedbackRepositoryImpl(get()) }
    single<PaymentRepository> { PaymentRepositoryImpl(get()) }
    single<RefundRepository> { RefundRepositoryImpl(get()) }
    single<SavedLocationRepository> { SavedLocationRepositoryImpl(get()) }
    single<NoteRepository> { NoteRepositoryImpl(get()) }
    single<ServiceCatalogRepository> { ServiceCatalogRepositoryImpl(get()) }
}

fun weatherifyIndexes(): List<IndexSpec> {
    val unique = IndexOptions().unique(true)
    return listOf(
        IndexSpec("users", Document("email", 1), unique),
        IndexSpec("users", Document("isPremium", 1)),
        IndexSpec("refunds", Document("refundId", 1), unique),
        IndexSpec("refunds", Document("paymentId", 1)),
        IndexSpec("refunds", Document("userEmail", 1)),
        IndexSpec("refunds", Document("status", 1)),
        IndexSpec("refunds", Document(mapOf("status" to 1, "createdAt" to -1))),
        IndexSpec("refunds", Document(mapOf("paymentId" to 1, "status" to 1))),
        IndexSpec("payments", Document("razorpay_payment_id", 1)),
        IndexSpec("payments", Document(mapOf("userEmail" to 1, "status" to 1))),
        IndexSpec("payments", Document(mapOf("serviceType" to 1, "status" to 1))),
        IndexSpec("payments", Document("createdAt", -1)),
        IndexSpec("payments", Document(mapOf("userEmail" to 1, "createdAt" to -1))),
        IndexSpec("payments", Document(mapOf("status" to 1, "createdAt" to -1))),
        IndexSpec("notes", Document(mapOf("userEmail" to 1, "createdAt" to -1))),
        IndexSpec("notes", Document("content", "text")),
        IndexSpec("saved_locations", Document("userEmail", 1)),
        IndexSpec("services", Document("serviceCode", 1), unique),
        IndexSpec("services", Document("status", 1)),
        IndexSpec("services", Document("createdAt", -1)),
        IndexSpec("services", Document(mapOf("status" to 1, "createdAt" to -1))),
        IndexSpec("service_history", Document("serviceId", 1)),
        IndexSpec("service_history", Document("changedAt", -1)),
    )
}
