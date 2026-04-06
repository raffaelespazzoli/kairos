package com.portal.cluster;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

@ApplicationScoped
public class ClusterService {

    public List<ClusterDto> listAll() {
        return Cluster.<Cluster>listAll().stream()
            .map(ClusterDto::from)
            .toList();
    }

    public ClusterDto findById(Long id) {
        Cluster cluster = Cluster.findById(id);
        if (cluster == null) {
            throw new NotFoundException("Cluster with ID " + id + " not found");
        }
        return ClusterDto.from(cluster);
    }

    @Transactional
    public ClusterDto create(CreateClusterRequest request) {
        if (Cluster.findByName(request.name()) != null) {
            throw new IllegalArgumentException("Cluster name '" + request.name() + "' already exists");
        }
        Cluster cluster = new Cluster();
        cluster.name = request.name();
        cluster.apiServerUrl = request.apiServerUrl();
        cluster.persist();
        return ClusterDto.from(cluster);
    }

    @Transactional
    public ClusterDto update(Long id, UpdateClusterRequest request) {
        Cluster cluster = Cluster.findById(id);
        if (cluster == null) {
            throw new NotFoundException("Cluster not found");
        }
        Cluster existing = Cluster.findByName(request.name());
        if (existing != null && !existing.id.equals(id)) {
            throw new IllegalArgumentException("Cluster name '" + request.name() + "' already exists");
        }
        cluster.name = request.name();
        cluster.apiServerUrl = request.apiServerUrl();
        return ClusterDto.from(cluster);
    }

    @Transactional
    public void delete(Long id) {
        Cluster cluster = Cluster.findById(id);
        if (cluster == null) {
            throw new NotFoundException("Cluster not found");
        }
        cluster.delete();
    }
}
