<script lang="ts">
  import { onMount } from 'svelte';
  import 'leaflet/dist/leaflet.css';

  export let lat: number;
  export let lng: number;
  export let zoom = 15;

  let map: any;
  let marker: any;

  onMount(async () => {
    const L = await import('leaflet');
    
    // Fix for default Leaflet icon paths in SvelteKit/Vite
    delete (L.Icon.Default.prototype as any)._getIconUrl;
    L.Icon.Default.mergeOptions({
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
    });

    map = L.map('map').setView([lat, lng], zoom);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors'
    }).addTo(map);

    marker = L.marker([lat, lng]).addTo(map)
      .bindPopup('Device Current Location')
      .openPopup();
  });

  $: if (map && marker) {
    map.setView([lat, lng], zoom);
    marker.setLatLng([lat, lng]);
  }
</script>

<div id="map" class="w-full h-full rounded-xl border border-edge"></div>

<style>
  :global(#map) {
    min-height: 300px;
  }
</style>
