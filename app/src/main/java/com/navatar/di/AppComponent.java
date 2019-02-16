package com.navatar.di;

import android.app.Application;
import com.navatar.ReferencePointApplication;
import com.navatar.data.source.MapsRepository;
import com.navatar.data.source.MapsRepositoryModule;
import com.navatar.data.source.RoutesRepository;
import com.navatar.data.source.RoutesRepositoryModule;
import com.navatar.util.schedulers.SchedulerModule;

import dagger.BindsInstance;
import dagger.Component;
import dagger.android.AndroidInjector;
import dagger.android.support.AndroidSupportInjectionModule;
import javax.inject.Singleton;

/**
 * This is a Dagger component. Refer to {@link ReferencePointApplication} for the list of Dagger components
 * used in this application.
 * <p>
 * Even though Dagger allows annotating a {@link Component} as a singleton, the code
 * itself must ensure only one instance of the class is created. This is done in {@link
 * ReferencePointApplication}.
 * //{@link AndroidSupportInjectionModule}
 * // is the module from Dagger.Android that helps with the generation
 * // and location of subcomponents.
 */
@Singleton
@Component(modules = {
        SchedulerModule.class,
        RoutesRepositoryModule.class,
        MapsRepositoryModule.class,
        ApplicationModule.class,
        ActivityBindingModule.class,
        AndroidSupportInjectionModule.class})
public interface AppComponent extends AndroidInjector<ReferencePointApplication> {

    MapsRepository getMapsRepository();

    RoutesRepository getNavHistoryRepository();

    // Gives us syntactic sugar. we can then do DaggerAppComponent.builder().application(this).build().inject(this);
    // never having to instantiate any modules or say which module we are passing the application to.
    // Application will just be provided into our app graph now.
    @Component.Builder
    interface Builder {

        @BindsInstance
        AppComponent.Builder application(Application application);

        AppComponent build();
    }
}
