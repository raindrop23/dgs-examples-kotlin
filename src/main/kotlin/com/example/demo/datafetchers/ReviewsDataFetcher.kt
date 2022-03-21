/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.demo.datafetchers

import com.example.demo.dataloaders.ReviewsDataLoader
import com.example.demo.generated.DgsConstants
import com.example.demo.generated.types.Image
import com.example.demo.generated.types.Review
import com.example.demo.generated.types.Show
import com.example.demo.generated.types.SubmittedReview
import com.example.demo.services.ReviewsService
import com.netflix.graphql.dgs.*
import org.dataloader.DataLoader
import org.reactivestreams.Publisher
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import reactor.core.publisher.Flux
import java.time.Instant

@DgsComponent
class ReviewsDataFetcher(private val reviewsService: ReviewsService) {

    /**
     * This datafetcher will be called to resolve the "reviews" field on a Show.
     * It's invoked for each individual Show, so if we would load 10 shows, this method gets called 10 times.
     * To avoid the N+1 problem this datafetcher uses a DataLoader.
     * Although the DataLoader is called for each individual show ID, it will batch up the actual loading to a single method call to the "load" method in the ReviewsDataLoader.
     * For this to work correctly, the datafetcher needs to return a CompletableFuture.
     */
    @DgsData(parentType = DgsConstants.SHOW.TYPE_NAME, field = DgsConstants.SHOW.Reviews)
    fun reviews(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<Review>> {
        //Instead of loading a DataLoader by name, we can use the DgsDataFetchingEnvironment and pass in the DataLoader classname.
        val reviewsDataLoader: DataLoader<Int, List<Review>> = dfe.getDataLoader(ReviewsDataLoader::class.java)

        //Because the reviews field is on Show, the getSource() method will return the Show instance.
        val show : Show = dfe.getSource()

        //Load the reviews from the DataLoader. This call is async and will be batched by the DataLoader mechanism.
        return reviewsDataLoader.load(show.id)
    }

    @DgsMutation
    fun addReview(@InputArgument review: SubmittedReview): List<Review> {
        reviewsService.saveReview(review)

        return reviewsService.reviewsForShow(review.showId)?: emptyList()
    }

    @DgsSubscription
    fun imageAdded(): Publisher<Image> = Flux.interval(Duration.ofMillis(333)).map({
        Image("https://dgs.example.com/${UUID.randomUUID().toString()}")
    }).doOnSubscribe { println("debug: ${Instant.now()}: subscription") }
        .doOnCancel { println("debug: ${Instant.now()}: cancel") }
        .doOnComplete { println("debug: ${Instant.now()}: complete") }
        .doOnError { println("debug: ${Instant.now()}: error") }
        .doFinally { println("debug: ${Instant.now()}: doOnFinally") }

    @DgsSubscription
    fun reviewAdded(@InputArgument showId: Int): Publisher<Review> {
        return reviewsService.getReviewsPublisher()
    }
}