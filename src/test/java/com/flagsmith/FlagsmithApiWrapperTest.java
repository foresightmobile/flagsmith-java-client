package com.flagsmith;

import static okhttp3.mock.MediaTypes.MEDIATYPE_JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.config.Retry;
import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.Flags;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mock.MockInterceptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class FlagsmithApiWrapperTest {

  private final String API_KEY = "OUR_API_KEY";
  private final String BASE_URL = "https://unit-test.com";
  private final ObjectMapper mapper = MapperFactory.getMapper();
  private FlagsmithApiWrapper sut;
  private FlagsmithLogger flagsmithLogger;
  private FlagsmithConfig defaultConfig;
  private MockInterceptor interceptor;

  @BeforeMethod(groups = "unit")
  public void init() {
    flagsmithLogger = mock(FlagsmithLogger.class);
    doThrow(new FlagsmithException("error Response")).when(flagsmithLogger)
        .httpError(any(), any(Response.class), eq(true));
    doThrow(new FlagsmithException("error IOException")).when(flagsmithLogger)
        .httpError(any(), any(IOException.class), eq(true));

    interceptor = new MockInterceptor();
    defaultConfig = FlagsmithConfig.newBuilder().addHttpInterceptor(interceptor).retries(new Retry(1)).baseUri(BASE_URL)
        .build();
    sut = new FlagsmithApiWrapper(defaultConfig, null, flagsmithLogger, API_KEY);
  }

  @Test(groups = "unit")
  public void getFeatureFlags_noUser_success() throws JsonProcessingException {
    // Arrange
    interceptor.addRule()
        .get(BASE_URL + "/flags/")
        .respond(mapper.writeValueAsString(Arrays.asList(getNewFlag())), MEDIATYPE_JSON);

    // Act
    final Flags actualFeatureFlags = sut.getFeatureFlags(true);

    // Assert
    assertEquals(newFlagsList(Arrays.asList(getNewFlag())), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void getFeatureFlags_noUser_fail() {
    // Arrange
    interceptor.addRule()
        .get(BASE_URL + "/flags/")
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    // Act
    final Flags actualFeatureFlags = sut.getFeatureFlags(false);

    // Assert
    assertEquals(newFlagsList(new ArrayList<>()), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any());
    verify(flagsmithLogger, times(1)).httpError(any(), any(Response.class), eq(false));
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_success() throws JsonProcessingException {
    // Arrange
    final List<TraitModel> traits = new ArrayList<TraitModel>(Arrays.asList(new TraitModel()));
    interceptor.addRule()
        .post(BASE_URL + "/identities/")
        .respond(
            mapper.writeValueAsString(
                getFlagsAndTraitsResponse(
                    Arrays.asList(getNewFlag()),
                    Arrays.asList(new TraitModel()))
            ), MEDIATYPE_JSON);

    // Act
    final Flags actualFeatureFlags = sut.identifyUserWithTraits(
        "user-w-traits", traits, true
    );

    // Assert
    Map<String, BaseFlag> flag1 = newFlagsList(Arrays.asList(getNewFlag())).getFlags();
    Map<String, BaseFlag> flag2 = actualFeatureFlags.getFlags();
    assertEquals(
        flag1, flag2
    );
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(0)).httpError(any(), any(Response.class), anyBoolean());
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  @Test(groups = "unit")
  public void identifyUserWithTraits_fail() {
    // Arrange
    final List<TraitModel> traits = new ArrayList<TraitModel>(Arrays.asList(new TraitModel()));
    interceptor.addRule()
        .post(BASE_URL + "/identities/")
        .respond(500, ResponseBody.create("error", MEDIATYPE_JSON));

    // Act
    final Flags actualFeatureFlags = sut.identifyUserWithTraits("user-w-traits", traits, false);

    // Assert
    assertEquals(newFlagsList(new ArrayList<>()), actualFeatureFlags);
    verify(flagsmithLogger, times(1)).info(anyString(), any(), any());
    verify(flagsmithLogger, times(1)).httpError(any(), any(Response.class), eq(false));
    verify(flagsmithLogger, times(0)).httpError(any(), any(IOException.class), anyBoolean());
  }

  private FeatureStateModel getNewFlag() {
    final FeatureModel feature = new FeatureModel();
    feature.setName("my-test-flag");
    feature.setId(123);
    final FeatureStateModel flag = new FeatureStateModel();
    flag.setFeature(feature);

    return flag;
  }

  private Flags newFlagsList(List<FeatureStateModel> flags) {
    return Flags.fromApiFlags(
        flags, null, defaultConfig.getFlagsmithFlagDefaults()
    );
  }

  private JsonNode getFlagsAndTraitsResponse(List<FeatureStateModel> flags, List<TraitModel> traits) {
    return FlagsmithTestHelper.getFlagsAndTraitsResponse(flags, traits);
  }
}
