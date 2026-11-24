package com.fixora.app.feature.requestform.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixora.app.core.network.ApiError
import com.fixora.app.core.network.ApiResult
import com.fixora.app.feature.account.data.AccountRepository
import com.fixora.app.feature.account.domain.Address
import com.fixora.app.feature.providerprofile.data.ProviderProfileRepository
import com.fixora.app.feature.providerprofile.domain.ProviderProfile
import com.fixora.app.feature.providerprofile.domain.ProviderService
import com.fixora.app.feature.requestform.data.CurrentAddressResolver
import com.fixora.app.feature.requestform.data.RequestFormRepository
import com.fixora.app.feature.requestform.data.RequestPhotoReader
import com.fixora.app.feature.requestform.domain.AddressChoice
import com.fixora.app.feature.requestform.domain.CurrentAddress
import com.fixora.app.feature.requestform.domain.RequestDraft
import com.fixora.app.feature.requestform.domain.RequestFormRules
import com.fixora.app.feature.requestform.domain.TimeSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

enum class WhenMode { Now, Scheduled }

/** Qué impide enviar; la pantalla lo explica junto al botón. */
enum class MissingField { Service, Description, Address, AddressLocation, Slot }

data class RequestFormUiState(
    val isLoading: Boolean = true,
    val loadError: ApiError? = null,
    val provider: ProviderProfile? = null,
    /** Distancia que mostró el mapa, ya escrita: «1.2». */
    val distanceKm: String? = null,
    val serviceId: String? = null,
    val problem: String? = null,
    val details: String = "",
    val photos: List<Uri> = emptyList(),
    val photoUnreadable: Boolean = false,
    val addresses: List<Address> = emptyList(),
    val addressChoice: AddressChoice? = null,
    val currentAddress: CurrentAddress? = null,
    val isResolvingCurrent: Boolean = false,
    val currentAddressFailed: Boolean = false,
    val whenMode: WhenMode = WhenMode.Now,
    val days: List<LocalDate> = emptyList(),
    val selectedDay: LocalDate? = null,
    val slots: List<TimeSlot> = emptyList(),
    val isLoadingSlots: Boolean = false,
    val slotsError: ApiError? = null,
    val selectedSlot: TimeSlot? = null,
    val isSending: Boolean = false,
    val sendError: ApiError? = null,
    val createdRequestId: String? = null,
) {
    val service: ProviderService? get() = provider?.services?.firstOrNull { it.id == serviceId }
    val suggestions: List<String> get() = service?.let { RequestFormRules.suggestions(it.name) }.orEmpty()
    val description: String get() = RequestFormRules.description(problem, details)
    val selectedAddress: Address? get() = (addressChoice as? AddressChoice.Saved)?.let { choice -> addresses.firstOrNull { it.id == choice.id } }
    val canAddPhoto: Boolean get() = photos.size < RequestFormRules.MAX_PHOTOS

    val missing: MissingField?
        get() = when {
            service == null -> MissingField.Service
            description.isBlank() -> MissingField.Description
            selectedAddress == null && !(addressChoice == AddressChoice.Current && currentAddress != null) -> MissingField.Address
            // Sin el punto en el mapa el técnico no sabe a dónde llegar (ANDROID-41).
            selectedAddress != null && selectedAddress?.location == null -> MissingField.AddressLocation
            whenMode == WhenMode.Scheduled && selectedSlot == null -> MissingField.Slot
            else -> null
        }
}

/** Formulario de solicitud y agendar cita (ANDROID-15, Figma «Fixora - Solicitar servicio»). */
@HiltViewModel
class RequestFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val profiles: ProviderProfileRepository,
    private val account: AccountRepository,
    private val repository: RequestFormRepository,
    private val photoReader: RequestPhotoReader,
    private val currentAddress: CurrentAddressResolver,
) : ViewModel() {

    private val branchId: String = checkNotNull(savedStateHandle["branchId"])

    /** Hoy en la zona del celular; las pruebas lo cambian. */
    internal var today: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) }
    internal var zone: ZoneId = ZoneId.systemDefault()
    internal var now: () -> Instant = Instant::now

    /**
     * Id para que la API no duplique la solicitud si se reintenta (client_uuid). Se guarda para
     * que sobreviva si Android recrea la pantalla.
     */
    private val clientUuid: String = savedStateHandle.get<String>(KEY_UUID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_UUID] = it }

    private val _uiState = MutableStateFlow(
        RequestFormUiState(distanceKm = savedStateHandle["distanceKm"], serviceId = savedStateHandle["categoryId"]),
    )
    val uiState: StateFlow<RequestFormUiState> = _uiState.asStateFlow()

    private var slotsJob: Job? = null

    init {
        loadProvider()
        watchAddresses()
    }

    fun retry() = loadProvider()

    fun onServiceSelected(id: String) {
        if (id == _uiState.value.serviceId) return
        // Otro servicio tiene otros problemas frecuentes y otros horarios libres.
        _uiState.update { it.copy(serviceId = id, problem = null, selectedSlot = null) }
        if (_uiState.value.whenMode == WhenMode.Scheduled) loadSlots()
    }

    fun onProblemSelected(problem: String) = _uiState.update { it.copy(problem = problem.takeUnless { p -> p == it.problem }) }

    fun onDetailsChange(text: String) = _uiState.update { it.copy(details = text.take(RequestFormRules.MAX_DESCRIPTION)) }

    fun onPhotosPicked(uris: List<Uri>) = _uiState.update { state ->
        state.copy(photos = (state.photos + uris).distinct().take(RequestFormRules.MAX_PHOTOS), photoUnreadable = false)
    }

    fun onPhotoRemoved(uri: Uri) = _uiState.update { it.copy(photos = it.photos - uri) }

    fun onAddressSelected(id: String) = _uiState.update { it.copy(addressChoice = AddressChoice.Saved(id), currentAddressFailed = false) }

    /** «Mi ubicación actual»: se convierte en dirección; si no se puede, se avisa y se queda la anterior. */
    fun onUseCurrentLocation() {
        _uiState.update { it.copy(isResolvingCurrent = true, currentAddressFailed = false) }
        viewModelScope.launch {
            val resolved = currentAddress.resolve()
            _uiState.update {
                if (resolved == null) {
                    it.copy(isResolvingCurrent = false, currentAddressFailed = true)
                } else {
                    it.copy(isResolvingCurrent = false, currentAddress = resolved, addressChoice = AddressChoice.Current)
                }
            }
        }
    }

    fun onWhenModeChange(mode: WhenMode) {
        if (mode == _uiState.value.whenMode) return
        if (mode == WhenMode.Scheduled) {
            val first = today()
            _uiState.update {
                it.copy(
                    whenMode = mode,
                    days = (0 until RequestFormRules.SCHEDULE_DAYS).map { day -> first.plusDays(day.toLong()) },
                    selectedDay = it.selectedDay ?: first,
                )
            }
            loadSlots()
        } else {
            _uiState.update { it.copy(whenMode = mode, selectedSlot = null) }
        }
    }

    fun onDaySelected(day: LocalDate) {
        if (day == _uiState.value.selectedDay) return
        _uiState.update { it.copy(selectedDay = day, selectedSlot = null) }
        loadSlots()
    }

    fun onSlotSelected(slot: TimeSlot) = _uiState.update { it.copy(selectedSlot = slot) }

    fun retrySlots() = loadSlots()

    fun send() {
        val state = _uiState.value
        if (state.isSending || state.missing != null) return
        val service = state.service ?: return
        _uiState.update { it.copy(isSending = true, sendError = null, photoUnreadable = false) }
        viewModelScope.launch {
            val photos = mutableListOf<ByteArray>()
            for (uri in state.photos) {
                photos += photoReader.read(uri) ?: run {
                    _uiState.update { it.copy(isSending = false, photoUnreadable = true) }
                    return@launch
                }
            }
            val keys = if (photos.isEmpty()) {
                emptyList()
            } else {
                when (val upload = repository.uploadPhotos(photos)) {
                    is ApiResult.Success -> upload.data
                    is ApiResult.Failure -> {
                        _uiState.update { it.copy(isSending = false, sendError = upload.error) }
                        return@launch
                    }
                }
            }
            val draft = RequestDraft(
                clientUuid = clientUuid,
                branchId = branchId,
                categoryId = service.id,
                description = state.description,
                savedAddressId = state.selectedAddress?.id,
                currentAddress = state.currentAddress.takeIf { state.addressChoice == AddressChoice.Current },
                photoKeys = keys,
                scheduledAt = state.selectedSlot?.startsAt?.takeIf { state.whenMode == WhenMode.Scheduled },
            )
            when (val result = repository.create(draft)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSending = false, createdRequestId = result.data) }
                is ApiResult.Failure -> _uiState.update { it.copy(isSending = false, sendError = result.error) }
            }
        }
    }

    private fun loadProvider() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            when (val result = profiles.profile(branchId)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val services = result.data.services
                    // El servicio que venía del mapa o, si solo ofrece uno, ese.
                    val serviceId = state.serviceId?.takeIf { id -> services.any { it.id == id } } ?: services.singleOrNull()?.id
                    state.copy(isLoading = false, provider = result.data, serviceId = serviceId)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    /** Si se agrega una dirección nueva desde aquí, se elige sola al regresar. */
    private fun watchAddresses() {
        viewModelScope.launch {
            account.addresses.catch { }.collect { addresses ->
                _uiState.update { state ->
                    val known = state.addresses.mapNotNull { it.id }.toSet()
                    val added = addresses.firstOrNull { it.id != null && state.addresses.isNotEmpty() && it.id !in known }
                    val stillThere = (state.addressChoice as? AddressChoice.Saved)?.takeIf { choice -> addresses.any { it.id == choice.id } }
                    val choice = when {
                        added?.id != null -> AddressChoice.Saved(added.id)
                        stillThere != null || state.addressChoice == AddressChoice.Current -> state.addressChoice
                        else -> (addresses.firstOrNull { it.isMain } ?: addresses.firstOrNull())?.id?.let(AddressChoice::Saved)
                    }
                    state.copy(addresses = addresses, addressChoice = choice)
                }
            }
        }
    }

    private fun loadSlots() {
        val state = _uiState.value
        val serviceId = state.serviceId ?: return
        val day = state.selectedDay ?: return
        slotsJob?.cancel()
        _uiState.update { it.copy(isLoadingSlots = true, slotsError = null, slots = emptyList()) }
        slotsJob = viewModelScope.launch {
            when (val result = repository.freeSlots(branchId, serviceId, day)) {
                is ApiResult.Success -> _uiState.update { current ->
                    // Solo los que empiezan ese día y todavía no pasan.
                    val slots = result.data.filter { it.startsAt.atZone(zone).toLocalDate() == day && it.startsAt.isAfter(now()) }
                    current.copy(isLoadingSlots = false, slots = slots)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoadingSlots = false, slotsError = result.error) }
            }
        }
    }

    private companion object {
        const val KEY_UUID = "clientUuid"
    }
}
